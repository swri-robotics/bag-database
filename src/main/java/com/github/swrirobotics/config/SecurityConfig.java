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

package com.github.swrirobotics.config;

import com.github.swrirobotics.account.UserService;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.ppolicy.PasswordPolicyAwareContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

@EnableWebSecurity
class SecurityConfig {
    @Autowired
    private ConfigService myConfigService;

    final private Logger myLogger = LoggerFactory.getLogger(SecurityConfig.class);

    private class CsrfAccessDeniedHandler extends AccessDeniedHandlerImpl {
        @Override
        public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                           AccessDeniedException e) throws IOException, ServletException {
            if (e instanceof MissingCsrfTokenException) {
                httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                super.handle(httpServletRequest, httpServletResponse, e);
            }
        }
    }

    @Configuration
    @Order(1)
    class AdminSecurityConfig extends WebSecurityConfigurerAdapter {
        @Autowired
        private Environment myEnvironment;

        @Bean
        public UserService userService() {
            return new UserService();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
            return new AjaxAuthenticationSuccessHandler("/");
        }

        @Override
        protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth
                    .eraseCredentials(true)
                    .userDetailsService(userService())
                    .passwordEncoder(passwordEncoder());
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            Set<String> profileSet = Sets.newHashSet(myEnvironment.getActiveProfiles());
            if (profileSet.contains("test")) {
                // CSRF protection is a pain to work around if we're doing unit tests;
                // disable it.
                http = http.csrf().disable();
            }

            http
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS).and()
                    .exceptionHandling().accessDeniedHandler(accessDeniedHandler()).and()
                    .antMatcher("/signin").authorizeRequests()
                    .and()
                    .formLogin()
                    .successHandler(authenticationSuccessHandler())
                    .loginPage("/signin")
                    .permitAll()
                    .and()
                    .logout()
                    .logoutUrl("/logout")
                    .permitAll()
                    .logoutSuccessUrl("/signin?logout");
        }

        private AccessDeniedHandler accessDeniedHandler() {
            return new SecurityConfig.CsrfAccessDeniedHandler() {
            };
        }
    }

    @Configuration
    @Order(2)
    public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
        @Autowired
        private Environment myEnvironment;

        @Bean
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
            return new AjaxAuthenticationSuccessHandler("/");
        }

        private AccessDeniedHandler accessDeniedHandler() {
            return new CsrfAccessDeniedHandler() {
            };
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            Set<String> profileSet = Sets.newHashSet(myEnvironment.getActiveProfiles());
            String ldapServer = myConfigService.getConfiguration().getLdapServer();
            // Only enable LDAP integration if a LDAP server is set in the configuration
            // or if we are running in unit test mode.
            if (profileSet.contains("test") || ldapServer != null && !ldapServer.isEmpty()) {
                http
                        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS).and()
                        .exceptionHandling().accessDeniedHandler(accessDeniedHandler()).and()
                        .authorizeRequests()
                        .antMatchers(
                                "/favicon.ico",
                                "/generalError",
                                "/resources/**",
                                "/signup").permitAll()
                        .anyRequest().fullyAuthenticated()
                        .and()
                        .formLogin()
                        .loginPage("/ldap_login")
                        .successHandler(authenticationSuccessHandler())
                        .permitAll()
                        .and()
                        .logout()
                        .logoutUrl("/logout").permitAll()
                        .logoutSuccessUrl("/ldap_login?logout");
            }
        }

        @Override
        public void configure(AuthenticationManagerBuilder auth) throws Exception {
            String ldapServer = myConfigService.getConfiguration().getLdapServer();
            if (ldapServer != null && !ldapServer.isEmpty()) {
                myLogger.info("Enabling LDAP authentication.");
                auth
                        .authenticationProvider(ldapAuthenticationProvider());
            }
        }

        @Bean
        public LdapAuthenticationProvider ldapAuthenticationProvider() throws Exception {
            LdapAuthenticationProvider lAP = new LdapAuthenticationProvider(ldapAuthenticator(), ldapAuthoritiesPopulator());
            return lAP;
        }

        private LdapAuthoritiesPopulator ldapAuthoritiesPopulator() throws Exception {
            String searchBase = myConfigService.getConfiguration().getLdapSearchBase();
            myLogger.info("LDAP search base: [" + searchBase + "]");
            return new DefaultLdapAuthoritiesPopulator(ldapContextSource(), searchBase);
        }

        @Bean
        public LdapContextSource ldapContextSource() throws Exception {
            String ldapProvider = myConfigService.getConfiguration().getLdapServer();
            myLogger.info("LDAP provider:  [" + ldapProvider + "]");

            if (ldapProvider.isEmpty()) {
                ldapProvider = "ldap://localhost:389/dc=springframework,dc=org";
            }
            PasswordPolicyAwareContextSource contextSource = new PasswordPolicyAwareContextSource(ldapProvider);
            String binddn = myConfigService.getConfiguration().getLdapBindDn();
            if (!binddn.isEmpty()) {
                contextSource.setUserDn(binddn);
                contextSource.setPassword(myConfigService.getConfiguration().getLdapBindPassword());
            }
            return contextSource;
        }

        @Bean
        public LdapAuthenticator ldapAuthenticator() throws Exception {
            BindAuthenticator authenticator = new BindAuthenticator(ldapContextSource());
            String userPattern = myConfigService.getConfiguration().getLdapUserPattern();
            myLogger.info("LDAP user pattern: [" + userPattern + "]");
            authenticator.setUserDnPatterns(new String[] {myConfigService.getConfiguration().getLdapUserPattern()});
            return authenticator;
        }
    }

}
