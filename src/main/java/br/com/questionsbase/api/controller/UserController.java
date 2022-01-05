package br.com.questionsbase.api.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.questionsbase.api.assembler.UserAssembler;
import br.com.questionsbase.api.exception.ErrorDetails.Field;
import br.com.questionsbase.api.exception.ResourceAlreadyExistsException;
import br.com.questionsbase.api.exception.ResourceNotFoundException;
import br.com.questionsbase.api.model.UserInput;
import br.com.questionsbase.api.model.UserResponse;
import br.com.questionsbase.api.model.dto.PasswordAndToken;
import br.com.questionsbase.domain.PasswordResetToken;
import br.com.questionsbase.domain.model.User;
import br.com.questionsbase.domain.model.User.Provider;
import br.com.questionsbase.domain.repository.UserRepository;
import br.com.questionsbase.domain.service.EmailService;
import br.com.questionsbase.domain.service.UserService;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/v1/user")
@AllArgsConstructor
public class UserController {
    private UserRepository userRepository;
    private UserService userService;
    private UserAssembler userAssembler;
    private JavaMailSender mailSender;
    private EmailService emailService;

    @GetMapping
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();

        return userAssembler.toCollectionResponse(users);
    }

    @GetMapping("/{userId}")
    public UserResponse getOneUser(@PathVariable int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> {
            Set<Field> fields = new HashSet<>();
            fields.add(new Field("userId", "Id given do not match"));
            throw new ResourceNotFoundException(fields);
        });

        return userAssembler.toResponse(user);
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody @Valid UserInput userInput) throws ResourceAlreadyExistsException {
        return userService.save(userInput);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable int userId) {
        userRepository.deleteFromLinkTable(userId);
        userRepository.deleteById(userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resetPassword")
    public void resetPassword(HttpServletRequest request, @RequestParam("email") String userEmail) {
        User user = userRepository.checkIfUserExistsAndRetrieveUser(userEmail, Provider.LOCAL).orElseThrow(() -> {
            Set<Field> fields = new HashSet<>();
            fields.add(new Field("email", "Email given do not match"));
            throw new ResourceNotFoundException(fields);
        });

        PasswordResetToken tokenSaved = userService.createPasswordResetTokenForUser(user);

        String url = request.getRequestURL().toString();

        int index = url.lastIndexOf('/');
        url = url.substring(0, index);

        mailSender.send(emailService.constructResetTokenEmail(url, request.getLocale(), tokenSaved.getToken(), user));
    }
    @Controller
    public class PageRenderer {
        @GetMapping("/api/v1/user/changePassword")
        public String displayResetPasswordPage(Model model, @RequestParam("token") String token) {
            userService.isTokenValid(token);
    
            return "password-reset"; 
        }
    }

    @PutMapping("/updatePassword")
    public UserResponse updatePassword(@RequestBody PasswordAndToken passwordAndToken){
        return userService.updatePassword(passwordAndToken.getNewPassword(), passwordAndToken.getToken());
    }
}
