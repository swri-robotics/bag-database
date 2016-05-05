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

Ext.define('BagDatabase.stores.BagStore', {
    extend: 'Ext.data.BufferedStore',
    model: 'BagDatabase.models.Bag',
    requires: ['BagDatabase.models.Bag'],
    pageSize: 100,
    proxy: {
        type: 'ajax',
        url: 'bags/search',
        reader: {
            type: 'json',
            rootProperty: 'bags',
            totalProperty: 'totalCount'
        },
        simpleSortMode: true,
        sortParam: 'sort',
        directionParam: 'dir',
        extraParams: {
            text: '',
            fields: ['messageType', 'topic']
        }
    },
    remoteSort: true,
    sorters: [{
        property: 'filename',
        direction: 'ASC'
    }],
    filterBags: function(terms, fields) {
        this.getProxy().setExtraParam('text', terms);
        this.getProxy().setExtraParam('fields', fields);
        this.load();
    },
    statics: {
        saveBags: function(bags, loadMask, button) {
            var oldEncodeDateFn = Ext.JSON.encodeDate;
            Ext.JSON.encodeDate = function(d) {
                return Ext.Date.format(d, '"Y-m-d\\TH:i:s') + "." + d.getMilliseconds() + '"';
            };
            if (loadMask) {
                loadMask.show();
            }
            var params = {};
            params[csrfName] = csrfToken;
            Ext.Ajax.request({
                url: 'bags/update',
                method: 'POST',
                params: params,
                jsonData: {
                    bags: bags,
                    totalCount: bags.length
                },
                callback: function(opts, success, response) {
                    if (loadMask) {
                        loadMask.hide();
                    }
                    if (!success) {
                        Ext.Msg.alert('Error', 'Unable to save bags.  Please check the log file for more details.');
                    }
                    else if (button) {
                        button.setDisabled(true);
                    }
                }
            });
            Ext.JSON.encodeDate = oldEncodeDateFn;
        }
    },
    autoLoad: true,
    initComponent: function() {
        this.proxy.extraParams[this.csrfName] = this.csrfToken;
        this.callParent(arguments);
    }
});