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

Ext.define('BagDatabase.views.AdminWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.adminWindow',
    layout: 'fit',
    title: 'Bag Database Administration',
    requires: ['BagDatabase.views.ErrorButton',
               'BagDatabase.views.StatusText'],
    constrainHeader: true,
    items: [{
        xtype: 'panel',
        itemId: 'adminPanel',
        bodyPadding: 5,
        layout: {
            type: 'vbox'
        },
        listeners: {
            afterrender: function(panel) {
                var statusText = panel.down('#statusText');
                if (statusText) {
                    statusText.connectWebSocket(panel.down('#errorButton'));
                }
            }
        },
        items: [{
            xtype: 'button',
            text: 'Force bag scan',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/forceScan'
                });
            }
        }, {
            xtype: 'button',
            text: 'Force full (slow!) bag scan',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/forceFullScan'
                });
            }
        }, {
            xtype: 'button',
            text: 'Re-scan all bag latitudes & longitudes',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/updateLatLons'
                });
            }
        }, {
            xtype: 'button',
            text: 'Re-scan all bag GPS paths',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/updateGpsPaths'
                });
            }
        }, {
            xtype: 'button',
            text: 'Update reverse-geocoded locations',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/updateLocations'
                });
            }
        }, {
            xtype: 'button',
            text: 'Re-scan all bag vehicle names',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/updateVehicleNames'
                });
            }
        }, {
            xtype: 'button',
            text: 'Re-scan all metadata tags',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/updateTags'
                });
            }
        }, {
            xtype: 'button',
            text: 'Remove DB entries for missing bags',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/removeMissingBags'
                });
            }
        }, {
            xtype: 'button',
            text: 'Remove bags with duplicate MD5 sums',
            margin: '5 0 0 0',
            handler: function() {
                Ext.Ajax.request({
                    url: 'admin/removeDuplicates'
                });
            }
        }, {
            xtype: 'button',
            text: 'Change the admin password',
            margin: '5 0 0 0',
            handler: function() {
                var win = Ext.create('Ext.window.Window', {
                    title: 'Change password',
                    layout: 'border',
                    height: 150,
                    width: 300,
                    defaultFocus: '#passwordField',
                    items: [{
                        xtype: 'form',
                        region: 'center',
                        layout: 'anchor',
                        referenceHolder: true,
                        defaultButton: 'submitButton',
                        defaults: {
                            margin: 5,
                            labelWidth: 150,
                            anchor: '100%'
                        },
                        items: [{
                            xtype: 'textfield',
                            inputType: 'password',
                            itemId: 'passwordField',
                            fieldLabel: 'New Password',
                            allowBlank: false,
                            previouslyValid: false,
                            validStateChanged: false,
                            validator: function(val) {
                                var confirmField, retval;
                                confirmField = this.up('form').down('#confirmField')
                                retval = true;
                                if (val != confirmField.getValue()) {
                                    retval = 'Passwords do not match.';
                                }

                                if (retval !== this.previouslyValid) {
                                    this.previouslyValid = retval;
                                    confirmField.validate();
                                }

                                return retval;
                            }
                        }, {
                            xtype: 'textfield',
                            inputType: 'password',
                            itemId: 'confirmField',
                            fieldLabel: 'Confirm Password',
                            allowBlank: false,
                            previouslyValid: false,
                            validStateChanged: false,
                            validator: function(val) {
                                var passwordField, retval;
                                passwordField = this.up('form').down('#passwordField')
                                retval = true;
                                if (val != passwordField.getValue()) {
                                    retval = 'Passwords do not match.';
                                }

                                if (retval !== this.previouslyValid) {
                                    this.previouslyValid = retval;
                                    passwordField.validate();
                                }
                                return retval;
                            }
                        }],
                        buttons: [{
                            text: 'Submit',
                            formBind: true,
                            reference: 'submitButton',
                            handler: function(button) {
                                var params = {
                                    password: button.up('form').down('#passwordField').getValue()
                                };
                                params[csrfName] = csrfToken;
                                Ext.Ajax.request({
                                    url: 'admin/changePassword',
                                    params: params,
                                    success: function() {
                                        Ext.Msg.alert('Success', 'Password changed.');
                                        button.up('window').close();
                                    },
                                    failure: function() {
                                        Ext.Msg.alert('Failure', 'Error changing password.');
                                    }
                                });
                            }
                        }]
                    }]
                });
                win.show();
            }
        }]
    }],
    initComponent: function() {
        if (this.showFBar) {
            this.width = 400;
            this.fbar = [{
                xtype: 'errorButton',
                itemId: 'errorButton'
            }, '-', {
                xtype: 'statusText',
                itemId: 'statusText'
            }, '->']
        }

        this.callParent(arguments);
    }
});