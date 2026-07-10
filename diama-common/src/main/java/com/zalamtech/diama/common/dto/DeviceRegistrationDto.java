package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Enregistrement d'un appareil mobile pour les notifications push (FCM / APNS). */
public record DeviceRegistrationDto(

        @NotBlank
        @Size(max = 500)
        String deviceToken,

        /** {@code IOS} | {@code ANDROID}. */
        @NotBlank
        @Pattern(regexp = "^(IOS|ANDROID)$", message = "plateforme invalide (IOS ou ANDROID)")
        String platform,

        @Size(max = 20)
        String appVersion
) {
}
