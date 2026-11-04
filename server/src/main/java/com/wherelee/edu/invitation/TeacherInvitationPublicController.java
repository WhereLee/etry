package com.wherelee.edu.invitation;

import com.wherelee.edu.invitation.dto.TeacherInvitationDtos;
import com.wherelee.edu.security.ClientIpResolver;
import com.wherelee.edu.security.PublicRequestThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/teacher-invitations")
public class TeacherInvitationPublicController {

    private final TeacherInvitationService service;
    private final ClientIpResolver clientIpResolver;
    private final PublicRequestThrottleService throttleService;
    private final InvitationProperties properties;

    public TeacherInvitationPublicController(TeacherInvitationService service,
                                             ClientIpResolver clientIpResolver,
                                             PublicRequestThrottleService throttleService,
                                             InvitationProperties properties) {
        this.service = service;
        this.clientIpResolver = clientIpResolver;
        this.throttleService = throttleService;
        this.properties = properties;
    }

    @PostMapping("/preview")
    public TeacherInvitationDtos.PreviewResponse preview(
            @Valid @RequestBody TeacherInvitationDtos.PreviewRequest request,
            HttpServletRequest httpRequest) {
        throttle("teacher-invite-preview", httpRequest);
        return service.preview(request.token());
    }

    @PostMapping("/accept")
    public Long accept(@Valid @RequestBody TeacherInvitationDtos.AcceptRequest request,
                       HttpServletRequest httpRequest) {
        throttle("teacher-invite-accept", httpRequest);
        return service.accept(request);
    }

    private void throttle(String scope, HttpServletRequest request) {
        throttleService.check(scope, clientIpResolver.resolve(request),
                properties.safeMaxAttempts(), properties.safeWindowMinutes());
    }
}
