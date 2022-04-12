// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

package com.github.swrirobotics.account;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest
public class UserServiceTest {

    @InjectMocks
    public UserService userService = new UserService();

    @Mock
    public AccountRepository accountRepositoryMock;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldInitializeWithOneDemoUser() {
        // act
        userService.insertTestUser();
        // assert
        verify(accountRepositoryMock, times(1)).save(any(Account.class));
    }

    @Test
    public void shouldThrowExceptionWhenUserNotFound() {
        // arrange
        thrown.expect(UsernameNotFoundException.class);
        thrown.expectMessage("user not found");

        when(accountRepositoryMock.findByEmail("user@example.com")).thenReturn(null);
        // act
        userService.loadUserByUsername("user@example.com");
    }

    @Test
    public void shouldReturnUserDetails() {
        // arrange
        Account demoUser = new Account("user@example.com", "demo", "ROLE_USER");
        when(accountRepositoryMock.findByEmail("user@example.com")).thenReturn(demoUser);

        // act
        UserDetails userDetails = userService.loadUserByUsername("user@example.com");

        // assert
        assertThat(demoUser.getEmail()).isEqualTo(userDetails.getUsername());
        assertThat(demoUser.getPassword()).isEqualTo(userDetails.getPassword());
        assertThat(hasAuthority(userDetails, demoUser.getRole()));
    }

    private boolean hasAuthority(UserDetails userDetails, String role) {
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        for(GrantedAuthority authority : authorities) {
            if(authority.getAuthority().equals(role)) {
                return true;
            }
        }
        return false;
    }
}
