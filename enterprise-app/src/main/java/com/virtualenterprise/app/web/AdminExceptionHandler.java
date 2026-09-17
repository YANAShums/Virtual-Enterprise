package com.virtualenterprise.app.web;
import com.virtualenterprise.app.service.AppException;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.web.dto.UserAdminForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
@ControllerAdvice(assignableTypes=AdminManagementController.class)
public class AdminExceptionHandler {
    private final AdminViewService views;
    public AdminExceptionHandler(AdminViewService views) {this.views=views;}
    @ExceptionHandler({AppException.class,DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class})
    String error(Exception ex,HttpServletRequest request,HttpServletResponse response,Model model) {
        int status=ex instanceof AppException app ? app.getStatus().value():409;
        String message=ex instanceof AppException app ? app.getMessage():ex instanceof ObjectOptimisticLockingFailureException
            ? "Запись уже изменена. Обновите страницу."
            : "Запись с таким кодом, логином, email или датой уже существует, либо используется другими записями.";
        response.setStatus(status);model.addAttribute("formError",message);
        Object form=request.getAttribute("adminForm");
        if(form!=null && status!=404) {
            if(form instanceof UserAdminForm user) user.setPassword(null);
            model.addAllAttributes(views.formReferences());model.addAttribute("form",form);
            model.addAttribute("section",request.getAttribute("adminSection"));model.addAttribute("recordId",request.getAttribute("adminRecordId"));
            return "admin/form";
        }
        return "admin/error";
    }
}
