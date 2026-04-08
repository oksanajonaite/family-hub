package com.familyhub.mapper;

import com.familyhub.dto.response.auth.AuthResponse;
import com.familyhub.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "userId", source = "id")
    AuthResponse toAuthResponse(User user);
}
