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

Ext.define('BagDatabase.views.BagUploadWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.bagUploadWindow',
    layout: 'fit',
    title: 'Upload Bags',
    iconCls: 'bag-add-icon',
    width: 400,
    height: 400,
    constraintHeader: true,
    items: [{
        xtype: 'form',
        bodyPadding: 5,
        itemId: 'scriptForm',
        url: 'bags/upload',
        layout: {
            type: 'vbox',
            align: 'stretch'
        },
        defaults: {
            labelWidth: 140,
            width: '100%'
        },
        items: [{
            xtype: 'textfield',
            name: 'targetDirectory',
            fieldLabel: 'Target Directory',
            allowBlank: false
        }, {
            xtype: 'filefield',
            name: 'file',
            fieldLabel: 'Bag File',
            allowBlank: false,
            buttonText: 'Select Bag...'
        }],
        buttons: [{
            text: 'Upload',
            handler: function() {
                var form, params;
                form = this.up('form').getForm();
                form.baseParams = {};
                form.baseParams[csrfName] = csrfToken;
                if (form.isValid()) {
                    form.submit({
                        waitMsg: 'Uploading bag file...',
                        success: function(fp, o) {
                            Ext.Msg.alert('Success', 'Your bag was uploaded.');
                        },
                        failure: function(fp, o) {
                            Ext.Msg.alert('Failure', 'Failed to upload bag.');
                        }
                    });
                }
            }
        }]
    }]
});
