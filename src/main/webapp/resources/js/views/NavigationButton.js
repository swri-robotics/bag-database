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

Ext.define('BagDatabase.views.NavigationButton', {
    extend: 'Ext.button.Split',
    alias: 'widget.navigationButton',
    text: 'Navigation',
    requires: ['BagDatabase.views.AboutWindow',
               'BagDatabase.views.LoginWindow'],
    isAdmin: false,
    listeners: {
        click: function(button, event) {
            button.showMenu(event);
        }
    },
    menu: [{
        text: 'About', iconCls: 'information-icon', handler: function() {
            var win = Ext.create('BagDatabase.views.AboutWindow');
            win.show();
        }
    }],
    initComponent: function() {
        var params = {};
        params[csrfName] = csrfToken;
        if (this.isAdmin) {
            this.menu.push({
                text: 'Administration', iconCls: 'database-icon', handler: function() {
                    var win = Ext.create('BagDatabase.views.AdminWindow');
                    win.show();
                }
            });
            this.menu.push({
                text: 'Configuration', iconCls: 'config-icon', handler: function() {
                    var win = Ext.create('BagDatabase.views.ConfigWindow', {
                        params: params
                    });
                    win.show();
                }
            });
            this.menu.push({
                text: 'Log Out', iconCls: 'logout-icon', handler: function() {
                    Ext.Ajax.request({
                        url: 'logout',
                        params: params,
                        callback: function() {
                            window.location = 'signin?logout';
                        }
                    });
                }
            });
        }
        else {
            this.menu.push({
                text: 'Admin Login', iconCls: 'login-icon', handler: function() {
                    var win = Ext.create('BagDatabase.views.LoginWindow');

                    win.show();
                    win.focus();
                }
            });
            this.menu.push({
                text: 'Log out', iconCls: 'logout-icon', handler: function() {
                    Ext.Ajax.request({
                        url: 'logout',
                        params: params,
                        callback: function() {
                           window.location = 'ldap_login?logout';
                        }
                    });
                }
            });
        }

        this.callParent(arguments);
    }
});