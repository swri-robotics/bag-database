---
# Feel free to add content and custom Front Matter to this file.
# To modify the layout, see https://jekyllrb.com/docs/themes/#overriding-theme-defaults

layout: default
title: LDAP Server Configuration
parent: Configuration
nav_order: 2
description: "Setting up an LDAP Server for Authentication"
permalink: /configuration/ldap
---

All these steps are inspired from this [blog](https://www.linux.com/topic/desktop/how-install-openldap-ubuntu-server-1804/).

1. Installation:
    ```
    sudo apt-get update
    sudo apt-get upgrade -y
    sudo apt-get install slapd ldap-utils -y
    ```

   During the setup, it will ask you to provide an Administrator password.

2. Configuration:
    ```
   sudo dpkg-reconfigure slapd
    ```

   It will ask you several questions for configuring SLAPD; these example values have been tested.

   - Omit: No (Keeps default configuration of the ldap server)
   - DNS domain name: example.com
   - Organisation name: Example Organization
   - Admin password: pwd (Keep same as the earlier one and confirm the same)
   - Database Type: MDB
   - Purging Database: Yes
   - Move old Database: Yes

3. There is an example file in this repository named `OpenLDAP_data.ldif` that will
   create a few organizational units in your LDAP database and then add a few users.
   You should customize this to suit your organization. The base structure of the
   file is also adapted from the [blog](https://www.linux.com/topic/desktop/how-install-openldap-ubuntu-server-1804/).
    ``` 
    ldapadd -x -D cn=admin,dc=example,dc=com -W -f OpenLDAP_data.ldif
    ```

   You will be asked for the `admin` password here.

4. The OpenLDAP server should be running by now, you can check the status with the
    following command:
    ```
    sudo systemctl status slapd
    ```

    If you need to manually restart or enable it use the following commands:
    ```
    sudo systemctl enable slapd
    sudo systemctl restart slapd
    ```

5. In case you need to remove OpenLDAP use the following commands taken from this [blog](https://installlion.com/ubuntu/xenial/main/s/slapd/uninstall/index.html):
    ```
    sudo apt-get remove --auto-remove slapd  
    ```

    And to completely purge remove:
    ```
    sudo apt-get purge --auto-remove slapd
    ```

6. Now you can launch the Bag Database.  If you used the example LDIF file to load users,
   you can log in with these credentials:
    - username: ben, password: benspassword
    - username: bob, password: bobspassword
