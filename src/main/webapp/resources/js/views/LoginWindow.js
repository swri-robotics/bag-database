// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
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

Ext.define('BagDatabase.views.LoginWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.loginWindow',
    title: 'Login',
    layout: 'fit',
    defaultFocus: '#passwordField',
    constrainHeader: true,
    items: [{
        url: 'signin',
        xtype: 'form',
        layout: 'anchor',
        referenceHolder: true,
        defaultButton: 'submitButton',
        itemId: 'loginForm',
        defaults: {
            margin: 5,
            labelWidth: 160,
            anchor: '100%'
        },
        items: [{
            xtype: 'hidden',
            name: 'username',
            value: 'admin'
        }, {
            xtype: 'textfield',
            fieldLabel: 'Administrative Password',
            itemId: 'passwordField',
            inputType: 'password',
            name: 'password',
            allowBlank: false
        }],
        buttons: [{
            text: 'Log In',
            reference: 'submitButton',
            formBind: true,
            disabled: true,
            handler: function() {
                var params = {};
                params[csrfName] = csrfToken;
                this.up('form').getForm().submit({
                    params: params,
                    success: function(form, action) {
                        window.location = window.location.pathname.replace("signin", "");
                    },
                    failure: function(form, action) {
                        Ext.Msg.alert('Error', 'Password incorrect.');
                    }
                });
            }
        }]
    }]
});