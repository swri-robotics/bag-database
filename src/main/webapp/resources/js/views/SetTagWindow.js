// *****************************************************************************
//
// Copyright (c) 2017, Southwest Research Institute® (SwRI®)
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

Ext.define('BagDatabase.views.SetTagWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.setTagWindow',
    title: 'Set Tag',
    layout: 'fit',
    defaultFocus: '#nameField',
    constrainHeader: true,
    items: [{
        url: 'bags/setTagForBags',
        xtype: 'form',
        layout: 'anchor',
        referenceHolder: true,
        defaultButton: 'submitButton',
        itemId: 'setTagForm',
        defaults: {
            margin: 5,
            labelWidth: 60,
            anchor: '100%'
        },
        items: [{
            xtype: 'textfield',
            name: 'tagName',
            itemId: 'nameField',
            fieldLabel: 'Name',
            allowBlank: false
        }, {
            xtype: 'textfield',
            fieldLabel: 'Value',
            itemId: 'valueField',
            name: 'value',
            allowBlank: true
        }, {
            xtype: 'hidden',
            itemId: 'bagIdField',
            name: 'bagIds'
        }],
        buttons: [{
            text: 'Set',
            reference: 'submitButton',
            formBind: true,
            disabled: true,
            iconCls: 'tag-add-icon',
            handler: function() {
                var params, tagWin;
                params = {};
                params[csrfName] = csrfToken;
                tagWin = this.up('window');
                this.up('form').getForm().submit({
                    params: params,
                    success: function(form, action) {
                        if (tagWin.tagGrid) {
                            tagWin.tagGrid.up('window').reloadTags();
                        }
                        if (tagWin.targetStores && tagWin.targetStores.forEach) {
                            tagWin.targetStores.forEach(function(store) {
                                store.load();
                            });
                        }
                        tagWin.close();
                    },
                    failure: function(form, action) {
                        Ext.Msg.alert('Error', 'Error occurred setting tag.');
                    }
                });
            }
        }]
    }],
    initComponent: function() {
        this.callParent(arguments);

        if (this.tagKey) {
            this.down('#nameField').setValue(this.tagKey);
        }
        if (this.tagValue) {
            this.down('#valueField').setValue(this.tagValue);
        }
        if (this.bagIds) {
            this.down('#bagIdField').setValue(this.bagIds);
        }
    }
});