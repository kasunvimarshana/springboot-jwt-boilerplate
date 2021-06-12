package lk.teachmeit.auth.service.impl;

import lk.teachmeit.auth.dto.*;
import lk.teachmeit.auth.enums.MailMode;
import lk.teachmeit.auth.exceptions.EmailAlreadyException;
import lk.teachmeit.auth.exceptions.EmailNotVerifiedException;
import lk.teachmeit.auth.exceptions.InvalidCodeException;
import lk.teachmeit.auth.exceptions.InvalidCredentialsException;
import lk.teachmeit.auth.model.User;
import lk.teachmeit.auth.service.HtmlProcessService;
import lk.teachmeit.auth.service.interfaces.AuthService;
import lk.teachmeit.auth.service.interfaces.UserService;
import lk.teachmeit.auth.util.EmailService;
import lk.teachmeit.auth.util.RandomValueUtil;
import lk.teachmeit.auth.util.TokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenProvider jwtTokenUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder bcryptEncoder;

    @Autowired
    private HtmlProcessService htmlProcessService;

    @Override
    public List<Object> signIn(LoginUserDto loginUserDto) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUserDto.getEmail(),
                            loginUserDto.getPassword()
                    )
            );
        } catch (Exception e) {
            throw new InvalidCredentialsException("User email or password incorrect");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final String token = jwtTokenUtil.generateToken(authentication);
        User user =  userService.findOne(loginUserDto.getEmail());
        if(user!=null) {
            if(user.getIsEmailVerified().equals("FALSE")) {
                throw new EmailNotVerifiedException("Your email is not verified yet");
            }
        }
        List<Object> results = new ArrayList<>();
        results.add(new AuthTokenDto(token));
        results.add(user);

        return results;
    }

    @Override
    public User signUp(UserDto userDto) throws IOException {
        if( userService.isUserExists(userDto.getEmail())){
            User user = new User();
            String resetCode = RandomValueUtil.getRandomNumberString(999999);
            user.setFirstName(userDto.getFirstName());
            user.setLastName(userDto.getLastName());
            user.setEmail(userDto.getEmail());
            user.setPassword(userDto.getPassword());
            user.setEmailVerifyCode(resetCode);
            user.setIsEmailVerified("FALSE");
            user.setPassword(bcryptEncoder.encode(userDto.getPassword()));
            Context emailContext = new Context();
            emailContext.setVariable("user", user);
            emailContext.setVariable("code", resetCode);
            emailService.sendHtml("Email Verification ", this.htmlProcessService.processHtml(emailContext, "email/email-verification"), MailMode.SMPT,user.getEmail());
            return userService.save(user);
        } else {
            throw new EmailAlreadyException("Email address already using");
        }
    }

    @Override
    public Object verifyEmail(ConfirmEmailDto confirmEmailDto) throws IOException {
        User authUser = userService.findOne(confirmEmailDto.getEmail());
        if( authUser != null){
            if(confirmEmailDto.getVerifyCode().equals(authUser.getEmailVerifyCode())) {
                authUser.setIsEmailVerified("TRUE");
                authUser.setEmailVerifyCode(null);
                userService.save(authUser);
                emailService.sendHtml("Email Verification", "Welcome to the Spring Email", MailMode.SMPT, authUser.getEmail());
                return authUser;
            } else {
                throw new InvalidCodeException("You provided verify code is wrong");
            }
        } else {
           throw new InvalidCredentialsException("Entered email address is not valid!");
        }
    }

    @Override
    public Object forgotPassword(String email) throws IOException {
        if(userService.isUserExists(email)){
            User authUser = userService.findOne(email);
            String resetCode = RandomValueUtil.getRandomNumberString(999999);
            authUser.setPasswordResetCode(resetCode);
            userService.save(authUser);

            Context emailContext = new Context();
            emailContext.setVariable("user", authUser);
            emailContext.setVariable("code", resetCode);

            emailService.sendHtml("Email Verification", this.htmlProcessService.processHtml(emailContext, "email/email-verification"), MailMode.SMPT, authUser.getEmail());

            return authUser;
        } else {
            throw new InvalidCredentialsException("User not found");
        }
    }

    @Override
    public Object checkPasswordVerifyCode(String email, String code) {
        if(userService.isUserExists(email)){
            User authUser = userService.findOne(email);
            authUser = userService.save(authUser);
            if(code.equals(authUser.getPasswordResetCode())) {
                return authUser;
            } else {
                throw new InvalidCodeException("Wrong reset code");
            }
        } else {
            throw new InvalidCredentialsException("User not found");
        }
    }

    @Override
    public Object resetPassword(String email, String code, String newPassword) throws IOException {
        if(userService.isUserExists(email)){
            User authUser = userService.findOne(email);
            if(code.equals(authUser.getPasswordResetCode())) {
                authUser.setPassword(bcryptEncoder.encode(newPassword));
                authUser.setPasswordResetCode(null);
                authUser = userService.save(authUser);
                emailService.sendHtml("Password reset successfully", "Your password reset successfully!", MailMode.SMPT, email);
                return authUser;
            } else {
                throw new InvalidCodeException("Wrong reset code");
            }
        } else {
            throw new InvalidCredentialsException("User not found");
        }
    }
}
