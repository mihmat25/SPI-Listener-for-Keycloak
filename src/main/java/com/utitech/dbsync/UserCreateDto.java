package com.utitech.dbsync;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class UserCreateDto {

    String username;
    String firstName;
    String lastName;
    String email;
}
