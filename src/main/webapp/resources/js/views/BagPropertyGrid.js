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

Ext.define('BagDatabase.views.BagPropertyGrid', {
    extend: 'Ext.grid.property.Grid',
    alias: 'widget.bagPropertyGrid',
    title: 'Properties',
    width: 300,
    split: true,
    hideHeaders: true,
    loadMask: null,
    nameColumnWidth: 130,
    listeners: {
        beforeedit: function(event, cell) {
            // The following fields can be edited; others cannot.
            switch (cell.record.id) {
                case 'description':
                case 'latitudeDeg':
                case 'location':
                case 'longitudeDeg':
                case 'name':
                case 'vehicle':
                return true;
            }
            return false;
        },
        edit: function(event, cell) {
            var newVal = cell.value;
            if (!newVal) {
                newVal = null;
            }
            if (cell.originalValue != newVal) {
                this.up('window').down('#saveButton').setDisabled(false);
            }
        }
    },
    buttons: [{
        text: 'Save',
        itemId: 'saveButton',
        disabled: true,
        handler: function(button) {
            var pgrid = button.up('window').down('propertygrid');
            if (!pgrid.loadMask) {
                pgrid.loadMask = new Ext.LoadMask(pgrid, {msg: 'Saving...'});
            }
            pgrid.loadMask.show();
            var bag = button.up('window').down('propertygrid').getSource();
            BagDatabase.stores.BagStore.saveBags([bag], pgrid.loadMask, button);
        }
    }],
    sourceConfig: {
        compressed: {
            displayName: 'Compressed?'
        },
        createdOn: {
            displayName: 'Created On',
            type: 'date',
            renderer: bagGridDateRenderer
        },
        description: {
            displayName: '<b>Description</b>'
        },
        duration: {
            displayName: 'Duration (s)'
        },
        endTime: {
            displayName: 'End Time',
            type: 'date',
            renderer: bagGridDateRenderer
        },
        filename: {
            displayName: 'Filename'
        },
        hasPath: {
            displayName: 'Has GPS Path?'
        },
        id: {
            displayName: 'Database Id'
        },
        indexed: {
            displayName: 'Indexed?'
        },
        latitudeDeg: {
            displayName: '<b>Latitude (deg)</b>',
            type: 'number',
            editor: Ext.create({
                xtype: 'numberfield',
                decimalPrecision: 10
            })
        },
        location: {
            displayName: '<b>Location</b>'
        },
        longitudeDeg: {
            displayName: '<b>Longitude (deg)</b>',
            type: 'number',
            editor: Ext.create({
                xtype: 'numberfield',
                decimalPrecision: 10
            })
        },
        md5sum: {
            displayName: 'MD5 Sum'
        },
        messageCount: {
            displayName: 'Message Count'
        },
        missing: {
            displayName: 'Missing?'
        },
        path: {
            displayName: 'Path'
        },
        size: {
            displayName: 'Size (MB)',
            renderer: function(value) {
                return (value / 1024.0 / 1024.0).toFixed(3);
            }
        },
        startTime: {
            displayName: 'Start Time',
            type: 'date',
            renderer: bagGridDateRenderer
        },
        updatedOn: {
            displayName: 'Updated On',
            type: 'date',
            renderer: function(value) {
                if (!value || value.getTime() === 0) {
                    return '';
                }
                return bagGridDateRenderer(value);
            }
        },
        vehicle: {
            displayName: '<b>Vehicle</b>'
        },
        version: {
            displayName: 'Bag Version'
        }
    }
});