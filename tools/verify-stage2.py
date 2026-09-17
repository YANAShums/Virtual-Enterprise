"""CRUD integration tests against an isolated, seeded stage-2 stack.
Set STAGE2_TEST_URL explicitly to http://localhost:18080. Creates test fixtures; do not run on production.
Uses actual HTTP, session/CSRF, Spring services, Liquibase and MySQL. No mock data sources.
"""
from html.parser import HTMLParser
import http.cookiejar
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ["STAGE2_TEST_URL"].rstrip("/")
if BASE != "http://localhost:18080":
    raise RuntimeError("Only the isolated stage-2 localhost:18080 stack is allowed")
PREFIX = "IT" + str(int(time.time()))


class Forms(HTMLParser):
    def __init__(self, text):
        super().__init__(); self.forms=[]; self.current=None; self.select=None; self.option=None; self.textarea=None
        self.feed(text)
    def add(self, name, value):
        self.current["fields"].setdefault(name, []).append(value)
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        if tag=="form":
            self.current={"action":a.get("action",""),"fields":{}};self.forms.append(self.current)
        elif self.current is not None:
            if tag=="input" and a.get("name"):
                if a.get("type") not in ("checkbox","radio") or "checked" in a:
                    self.add(a["name"],a.get("value",""))
            elif tag=="select": self.select={"name":a.get("name"),"multiple":"multiple" in a,"options":[]}
            elif tag=="option" and self.select is not None: self.option={"value":a.get("value",""),"selected":"selected" in a}
            elif tag=="textarea": self.textarea={"name":a.get("name"),"text":""}
    def handle_data(self,data):
        if self.textarea is not None:self.textarea["text"]+=data
    def handle_endtag(self,tag):
        if tag=="form":self.current=None
        elif tag=="option" and self.select is not None and self.option is not None:
            self.select["options"].append(self.option);self.option=None
        elif tag=="select" and self.select is not None:
            selected=[o["value"] for o in self.select["options"] if o["selected"]]
            if not selected and not self.select["multiple"] and self.select["options"]:selected=[self.select["options"][0]["value"]]
            for value in selected:self.add(self.select["name"],value)
            self.select=None
        elif tag=="textarea" and self.textarea is not None:
            self.add(self.textarea["name"],self.textarea["text"]);self.textarea=None


class Client:
    def __init__(self):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(self,path,data=None,headers=None):
        req=urllib.request.Request(BASE+path,data=data,headers=headers or {})
        try:
            with self.opener.open(req,timeout=25) as r:return r.status,r.read().decode(),r.url
        except urllib.error.HTTPError as e:return e.code,e.read().decode(),e.url
    def form(self,path):
        status,html,url=self.request(path);assert status==200,(path,status)
        forms=Forms(html).forms
        return next(f for f in forms if f["action"].startswith("/admin/") or f["action"] in ("/login","/employees"))
    def submit(self,form,values=None):
        fields=dict(form["fields"]);fields.update(values or {})
        data=urllib.parse.urlencode(fields,doseq=True).encode()
        return self.request(form["action"],data)
    def save(self,section,values,id=None,expected=200):
        form=self.form("/admin/"+section+("/new" if id is None else f"/{id}/edit"))
        result=self.submit(form,values)
        assert result[0]==expected,(section,result[0],result[1][:200])
        return result
    def login(self,user,password,success=True):
        result=self.submit(self.form("/login"),{"username":user,"password":password})
        assert ("/login" not in result[2])==success,("login",user,result[0],result[2])
        return self
    def id(self,section,marker):
        status,html,_=self.request("/admin?section="+section);assert status==200
        for row in re.findall(r'<tr class="admin-row"[^>]*>(.*?)</tr>',html,re.S):
            if marker in row:
                match=re.search(r'/admin/'+section+r'/(\d+)/edit',row)
                if match:return int(match.group(1))
        raise AssertionError("Record not found: "+section+" "+marker)
    def delete(self,section,id,expected=200):
        result=self.submit(self.form(f"/admin/{section}/{id}/delete"))
        assert result[0]==expected,("delete",section,result[0])
    def preview(self,employee_id,date="2030-01-07"):
        status,html,_=self.request("/tasks");assert status==200
        token=re.search(r'name="csrf-token" content="([^"]+)"',html).group(1)
        header=re.search(r'name="csrf-header" content="([^"]+)"',html).group(1)
        payload={"projectId":1,"candidateIds":[employee_id],"plannedStartDate":date,"plannedEndDate":date,"plannedHours":1}
        status,body,_=self.request("/api/v1/tasks/workload-preview",json.dumps(payload).encode(),{"Content-Type":"application/json",header:token})
        assert status==200,("preview",status,body[:150])
        return json.loads(body)["candidates"][0]["daily"][0]["availableHours"]


def main():
    admin=Client().login("admin","admin123")
    for section in ("departments","positions","schedules","calendar","users","roles","settings"):
        assert admin.request("/admin?section="+section)[0]==200
    # Real CRUD, duplicate constraints, cycles, optimistic concurrency and referenced delete.
    admin.save("departments",{"code":PREFIX+"D","name":"Integration parent","active":"true"})
    dep=admin.id("departments",PREFIX+"D")
    admin.save("departments",{"code":PREFIX+"C","name":"Integration child","parentId":dep,"active":"true"})
    child=admin.id("departments",PREFIX+"C")
    admin.save("departments",{"parentId":child},dep,409)
    admin.delete("departments",dep,409)
    admin.save("departments",{"code":PREFIX+"D","name":"Duplicate"},expected=409)
    stale=admin.form(f"/admin/departments/{dep}/edit")
    admin.save("departments",{"name":"Renamed parent","managerId":1},dep)
    assert admin.submit(stale,{"name":"Stale edit"})[0]==409
    print("PASS: department CRUD, hierarchy cycle, duplicate, manager, stale edit and referenced deletion",flush=True)
    admin.save("positions",{"code":PREFIX+"P","name":"Integration position","grade":"Junior","description":"Description","active":"true"})
    pos=admin.id("positions",PREFIX+"P")
    admin.save("positions",{"description":"Updated description","grade":"Middle"},pos)
    admin.save("schedules",{"code":PREFIX+"S","name":"Integration schedule","active":"true",**{f"hours[{i}]":6 if i<5 else 0 for i in range(7)}})
    schedule=admin.id("schedules",PREFIX+"S")
    bad=admin.save("schedules",{"hours[0]":25},schedule)
    assert "Проверьте заполнение формы" in bad[1]
    admin.save("schedules",{"timezone":"Invalid/Zone"},schedule,409)
    # Create employee through existing UI to prove new references work in real workload calculation.
    result=admin.submit(admin.form("/employees/new"),{"personnelNumber":PREFIX,"lastName":PREFIX,"firstName":"Test","hireDate":"2026-01-01","departmentId":dep,"positionId":pos,"workScheduleId":schedule,"employmentRate":"1"})
    assert result[0]==200 and "/employees?created" in result[2],result[2]
    _,html,_=admin.request("/admin?section=employees")
    row=next(row for row in re.findall(r'<tr class="admin-row"[^>]*>(.*?)</tr>',html,re.S) if PREFIX in row)
    employee=int(re.search(r'/employees/(\d+)',row).group(1))
    assert admin.preview(employee)==6
    stale_schedule=admin.form(f"/admin/schedules/{schedule}/edit")
    admin.save("schedules",{"hours[0]":7},schedule)
    assert admin.preview(employee)==7
    assert admin.submit(stale_schedule,{"hours[0]":5})[0]==409
    admin.save("calendar",{"calendarDate":"2030-01-07","dayType":"HOLIDAY","workingHoursOverride":0,"description":PREFIX})
    day=admin.id("calendar",PREFIX)
    assert admin.preview(employee)==0
    admin.save("calendar",{"dayType":"SHORTENED","workingHoursOverride":4},day)
    assert admin.preview(employee)==4
    admin.save("calendar",{"calendarDate":"2030-01-07","dayType":"HOLIDAY","workingHoursOverride":0},expected=409)
    admin.delete("calendar",day)
    assert admin.preview(employee)==7
    admin.delete("positions",pos,409);admin.delete("schedules",schedule,409)
    admin.save("positions",{"active":[]},pos)
    options=admin.form("/employees/new")["fields"]
    _,html,_=admin.request("/employees/new")
    assert f'value="{pos}">Integration position' not in html
    forged=admin.submit(admin.form("/employees/new"),{"personnelNumber":PREFIX+"F","lastName":"Forge","firstName":"Test","hireDate":"2026-01-01","departmentId":dep,"positionId":pos,"workScheduleId":schedule,"employmentRate":"1"})
    assert "действующие подразделение" in forged[1]
    print("PASS: position/schedule CRUD, archive guards, calendar exceptions change actual workload hours",flush=True)
    # Existing account sessions must be revoked on block, role change and password reset.
    username=PREFIX.lower()
    admin.save("users",{"username":username,"email":username+"@example.test","password":"Test-password-123","roleIds":[5],"employeeId":employee})
    user=admin.id("users",username)
    session=Client().login(username,"Test-password-123")
    assert session.request("/admin")[0]==403
    admin.save("users",{"status":"BLOCKED"},user)
    assert "/login" in session.request("/dashboard")[2]
    Client().login(username,"Test-password-123",False)
    admin.save("users",{"status":"ACTIVE"},user)
    session=Client().login(username,"Test-password-123")
    admin.save("users",{"password":"Reset-password-123"},user)
    assert "/login" in session.request("/dashboard")[2]
    Client().login(username,"Test-password-123",False)
    session=Client().login(username,"Reset-password-123")
    admin.save("users",{"roleIds":[6]},user)
    assert "/login" in session.request("/dashboard")[2]
    admin.delete("users",user)
    Client().login(username,"Reset-password-123",False)
    admin.save("users",{"status":"BLOCKED"},1,409)
    admin.save("users",{"roleIds":[5]},1,409)
    print("PASS: user creation/link, role assignment, block/unblock, reset, archive, active-session revocation, last-admin protection",flush=True)
    # Mutations require CSRF, and a non-admin cannot bypass UI restrictions.
    form=admin.form("/admin/positions/new");form["fields"].pop("_csrf",None)
    assert admin.submit(form,{"code":"NO_CSRF","name":"Blocked"})[0]==403
    observer=Client().login("employee","employee123")
    for section in ("departments","positions","schedules","calendar","users"):
        assert observer.request("/admin/"+section+"/new")[0]==403
    # Delete unused entities and save enterprise settings through UI.
    admin.delete("departments",child)
    admin.save("positions",{"code":PREFIX+"DEL","name":"Delete test"})
    admin.delete("positions",admin.id("positions",PREFIX+"DEL"))
    admin.save("schedules",{"code":PREFIX+"DEL","name":"Delete schedule"})
    admin.delete("schedules",admin.id("schedules",PREFIX+"DEL"))
    result=admin.submit(admin.form("/admin/settings/edit"),{"name":"Stage 2 acceptance organization"})
    assert result[0]==200
    assert admin.form("/admin/settings/edit")["fields"]["name"]==["Stage 2 acceptance organization"]
    print("PASS: CSRF, role enforcement, deletion and enterprise settings; all stage-2 integration checks passed",flush=True)


if __name__=="__main__":
    main()
