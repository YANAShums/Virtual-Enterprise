--liquibase formatted sql
--changeset virtual-enterprise:core-002
INSERT INTO enterprises(id, code, name, timezone, active, version)
VALUES (1, 'DEMO', 'Виртуальное предприятие', 'Europe/Minsk', TRUE, 0);

INSERT INTO roles(id, code, name, active) VALUES
(1, 'ADMIN', 'Администратор', TRUE),
(2, 'ENTERPRISE_MANAGER', 'Руководитель предприятия', TRUE),
(3, 'DEPARTMENT_MANAGER', 'Руководитель подразделения', TRUE),
(4, 'PROJECT_MANAGER', 'Руководитель проекта', TRUE),
(5, 'EMPLOYEE', 'Сотрудник', TRUE),
(6, 'OBSERVER', 'Наблюдатель', TRUE);

INSERT INTO users(id, username, email, password_hash, status, active, version) VALUES
(1, 'admin', 'admin@virtual.local', '$2y$10$lyZ9FfShe4DiItcc6Y7n8eFdj0P8hrjR7ZoN1cmfYdeulWb0ty41m', 'ACTIVE', TRUE, 0),
(2, 'manager', 'manager@virtual.local', '$2y$10$Sfznj9bKkvE60IihAfI7x.HtTJ.FRMo1gWrV9wiUIF3OBkva8FRKe', 'ACTIVE', TRUE, 0),
(3, 'employee', 'employee@virtual.local', '$2y$10$BGOCx73VxUDRKfxpyeRpE.WwIxdLGwZsTp10/qOuvYqd5.aOL1rQK', 'ACTIVE', TRUE, 0);

INSERT INTO user_roles(user_id, role_id) VALUES
(1,1),(1,2),(1,4),(2,4),(3,5);

INSERT INTO departments(id, enterprise_id, parent_department_id, code, name, active, version) VALUES
(1,1,NULL,'MANAGEMENT','Руководство',TRUE,0),
(2,1,NULL,'DEVELOPMENT','Отдел разработки',TRUE,0),
(3,1,NULL,'QA','Отдел тестирования',TRUE,0),
(4,1,NULL,'ANALYTICS','Отдел аналитики',TRUE,0);

INSERT INTO positions(id, enterprise_id, code, name, grade, active, version) VALUES
(1,1,'DIRECTOR','Руководитель предприятия',NULL,TRUE,0),
(2,1,'PM','Руководитель проекта','Senior',TRUE,0),
(3,1,'BACKEND','Backend-разработчик','Middle',TRUE,0),
(4,1,'FRONTEND','Frontend-разработчик','Middle',TRUE,0),
(5,1,'QA','QA-инженер','Middle',TRUE,0),
(6,1,'BA','Бизнес-аналитик','Middle',TRUE,0);

INSERT INTO work_schedules(id, enterprise_id, code, name, weekly_hours, timezone, version)
VALUES (1,1,'FIVE_DAYS_40','Пятидневная рабочая неделя',40.00,'Europe/Minsk',0);
INSERT INTO work_schedule_days(schedule_id, day_of_week, working_day, planned_hours) VALUES
(1,1,TRUE,8),(1,2,TRUE,8),(1,3,TRUE,8),(1,4,TRUE,8),(1,5,TRUE,8),(1,6,FALSE,0),(1,7,FALSE,0);

INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (1,1,1,1,1,NULL,1,'EMP-001','Смирнов','Александр','Игоревич','admin@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 900 DAY),1.00,'Europe/Minsk','ACTIVE',TRUE,0);
INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (2,1,2,2,2,1,1,'EMP-002','Петров','Пётр','Андреевич','manager@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 700 DAY),1.00,'Europe/Minsk','ACTIVE',TRUE,0);
INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (3,1,3,2,3,2,1,'EMP-003','Иванов','Иван','Сергеевич','employee@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 500 DAY),1.00,'Europe/Minsk','ACTIVE',TRUE,0);
INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (4,1,NULL,2,4,2,1,'EMP-004','Сидорова','Анна','Олеговна','sidorova@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 400 DAY),1.00,'Europe/Minsk','ACTIVE',TRUE,0);
INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (5,1,NULL,3,5,1,1,'EMP-005','Кузнецов','Максим','Ильич','kuznetsov@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 350 DAY),1.00,'Europe/Minsk','ACTIVE',TRUE,0);
INSERT INTO employees(id, enterprise_id, user_id, department_id, position_id, manager_employee_id, work_schedule_id,
 personnel_number,last_name,first_name,middle_name,email,hire_date,employment_rate,timezone,status,active,version)
VALUES (6,1,NULL,4,6,1,1,'EMP-006','Орлова','Елена','Викторовна','orlova@virtual.local',DATE_SUB(CURRENT_DATE, INTERVAL 300 DAY),0.75,'Europe/Minsk','ACTIVE',TRUE,0);

INSERT INTO projects(id, enterprise_id, manager_employee_id, code, name, description, status, priority,
 planned_start_date, planned_end_date, planned_hours, progress_percent, risk_level, active, version) VALUES
(1,1,2,'PORTAL','Корпоративный портал','Разработка внутреннего портала предприятия','ACTIVE','HIGH',DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY),DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY),1200,35,'MEDIUM',TRUE,0),
(2,1,2,'CRM','CRM-система','Автоматизация работы с клиентами','ACTIVE','MEDIUM',DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),DATE_ADD(CURRENT_DATE, INTERVAL 120 DAY),900,15,'LOW',TRUE,0);

INSERT INTO project_members(project_id, employee_id, project_role, joined_at, active) VALUES
(1,2,'PROJECT_MANAGER',DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY),TRUE),
(1,3,'BACKEND_DEVELOPER',DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY),TRUE),
(1,4,'FRONTEND_DEVELOPER',DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY),TRUE),
(1,5,'QA_ENGINEER',DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY),TRUE),
(1,6,'BUSINESS_ANALYST',DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY),TRUE),
(2,2,'PROJECT_MANAGER',DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),TRUE),
(2,3,'BACKEND_DEVELOPER',DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),TRUE),
(2,5,'QA_ENGINEER',DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),TRUE);

INSERT INTO task_statuses(id, enterprise_id, code, name, column_order, color, active) VALUES
(1,1,'PLANNED','Запланировано',1,'#6c757d',TRUE),
(2,1,'IN_PROGRESS','В работе',2,'#0d6efd',TRUE),
(3,1,'REVIEW','На проверке',3,'#6f42c1',TRUE),
(4,1,'DONE','Выполнено',4,'#198754',TRUE),
(5,1,'CANCELLED','Отменено',5,'#dc3545',TRUE);

INSERT INTO task_priorities(id, code, name, weight, color, active) VALUES
(1,'LOW','Низкий',1,'#6c757d',TRUE),
(2,'MEDIUM','Средний',2,'#0dcaf0',TRUE),
(3,'HIGH','Высокий',3,'#fd7e14',TRUE),
(4,'CRITICAL','Критический',4,'#dc3545',TRUE);

INSERT INTO tasks(id, project_id, task_number, title, description, creator_user_id, assignee_employee_id,
 assignee_department_id,status_id,priority_id,planned_start_date,planned_end_date,planned_hours,actual_hours,
 distribution_mode,overdue,overload_confirmed,kanban_order,version) VALUES
(1,1,'PORTAL-001','Спроектировать модель данных','Подготовить структуру основных сущностей',2,6,4,4,2,DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY),DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),24,22,'EQUAL',FALSE,FALSE,1000,0),
(2,1,'PORTAL-002','Реализовать API сотрудников','CRUD и фильтрация сотрудников',2,3,2,2,3,CURRENT_DATE,DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY),32,4,'EQUAL',FALSE,FALSE,1000,0),
(3,1,'PORTAL-003','Сверстать Kanban-доску','Bootstrap и drag-and-drop',2,4,2,2,3,CURRENT_DATE,DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY),28,6,'EQUAL',FALSE,FALSE,2000,0),
(4,1,'PORTAL-004','Подготовить тест-кейсы','Проверка переходов статусов',2,5,3,1,2,DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY),DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY),24,0,'EQUAL',FALSE,FALSE,1000,0),
(5,2,'CRM-001','Согласовать требования','Интервью с заказчиком',2,6,4,3,3,DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY),DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY),20,12,'EQUAL',FALSE,FALSE,1000,0),
(6,2,'CRM-002','Создать сервис клиентов','Базовый REST API',2,3,2,1,4,CURRENT_DATE,DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY),36,0,'EQUAL',FALSE,FALSE,1000,0);

INSERT INTO employee_absences(employee_id, absence_type, status, start_at, end_at, all_day, version)
VALUES (5,'VACATION','APPROVED',DATE_ADD(CURRENT_DATE, INTERVAL 12 DAY),DATE_ADD(CURRENT_DATE, INTERVAL 16 DAY),TRUE,0);
