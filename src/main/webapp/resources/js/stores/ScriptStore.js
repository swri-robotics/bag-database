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

Ext.define('BagDatabase.stores.ScriptStore', {
    extend: 'Ext.data.Store',
    model: 'BagDatabase.models.Script',
    requires: ['BagDatabase.models.Script',
               'BagDatabase.models.ScriptCriteria'],
    storeId: 'scriptStore',
    proxy: {
        type: 'ajax',
        url: 'scripts/list',
        reader: {
            type: 'json',
            rootProperty: 'scripts',
            totalProperty: 'totalCount'
        }
    },
    remoteSort: false,
    autoLoad: true,
    initComponent: function() {
        this.callParent(arguments);
    },
    runScript: function(scriptId, bagIds) {
        var params = {
            scriptId: scriptId,
            bagIds: bagIds
        };
        params[csrfName] = csrfToken;
        Ext.Ajax.request({
            url: 'scripts/run',
            params: params,
            success: function(response, opts) {
                var responseObj = JSON.parse(response.responseText);
                if (responseObj && responseObj.success) {
                    Ext.Msg.show({
                        title: 'Run Success',
                        message: 'Script was succesfully started.',
                        buttons: Ext.Msg.OK,
                        icon: Ext.Msg.INFO
                    });
                }
                else {
                    Ext.Msg.show({
                        title: 'Run Failiure',
                        message: 'Failed to run script: ' + responseObj.message,
                        buttons: Ext.Msg.OK,
                        icon: Ext.Msg.ERROR
                    })
                }
            },
            failure: function(response, opts) {
                Ext.Msg.show({
                    title: 'Run Failure',
                    message: 'Failed to run script; error code: ' + response.status,
                    buttons: Ext.Msg.OK,
                    icon: Ext.Msg.ERROR
                });
            }
        })
    }
});