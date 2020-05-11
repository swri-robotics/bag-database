package com.github.swrirobotics.ldap;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class LdapLoginController {

    @RequestMapping(value = "ldap_login")
    public String ldap_login() {
        return "ldap/ldap_login";
    }

}
