package com.virtualenterprise.app.web;
import com.virtualenterprise.app.service.AppException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes=MlWorkflowController.class)
public class MlWorkflowExceptionHandler {
    @ExceptionHandler(AppException.class)
    ModelAndView handle(AppException error) {
        ModelAndView view=new ModelAndView("admin/error");
        view.setStatus(error.getStatus());
        view.addObject("formError",error.getMessage());
        return view;
    }
}
