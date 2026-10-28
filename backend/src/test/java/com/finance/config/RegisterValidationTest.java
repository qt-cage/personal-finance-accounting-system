package com.finance.config;
import com.finance.entity.dto.RegisterDTO;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class RegisterValidationTest {
    @Test void acceptsMissingOrFiftyCharacterNicknameButRejectsFiftyOne() {
        try (ValidatorFactory factory=Validation.buildDefaultValidatorFactory()) {
            RegisterDTO dto=new RegisterDTO(); dto.setUsername("tester"); dto.setPassword("test-password");
            assertTrue(factory.getValidator().validate(dto).isEmpty());
            dto.setNickname(String.join("",Collections.nCopies(50,"名")));
            assertTrue(factory.getValidator().validate(dto).isEmpty());
            dto.setNickname(dto.getNickname()+"名");
            assertTrue(factory.getValidator().validate(dto).stream().anyMatch(error->error.getPropertyPath().toString().equals("nickname")));
        }
    }
}
