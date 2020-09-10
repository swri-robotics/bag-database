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

Ext.define('BagDatabase.views.ScriptWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.scriptWindow',
    layout: 'fit',
    title: 'Script Properties',
    width: 1100,
    height: 700,
    constrainHeader: true,
    items: [{
        xtype: 'form',
        bodyPadding: 5,
        itemId: 'scriptForm',
        url: 'scripts/save',
        defaultType: 'textfield',
        jsonSubmit: true,
        layout: {
            type: 'vbox',
            align: 'stretch'
        },
        defaults: {
            labelWidth: 140,
            width: '100%'
        },
        items: [{
            fieldLabel: 'Id',
            name: 'id',
            xtype: 'hidden',
            value: 0
        }, {
            fieldLabel: 'Created On',
            name: 'createdOn',
            xtype: 'hidden',
            value: 0
        }, {
            fieldLabel: 'Updated On',
            name: 'updatedOn',
            xtype: 'hidden',
            value: 0
        }, {
            fieldLabel: 'Name',
            name: 'name',
            itemId: 'name',
            allowBlank: false,
            tooltipHtml: 'Short, friendly name for the script'
        }, {
            fieldLabel: 'Allow Network Access',
            name: 'allowNetworkAccess',
            xtype: 'checkboxfield',
            uncheckedValue: false,
            inputValue: true,
            tooltipHtml: 'Check to allow the container to access the network'
        }, {
            fieldLabel: 'Description',
            name: 'description',
            tooltipHtml: 'Longer, friendly description for the script'
        }, {
            fieldLabel: 'Docker Image',
            name: 'dockerImage',
            allowBlank: false,
            tooltipHtml: 'Docker image that will be used as a container for the script'
        }, {
            fieldLabel: 'Memory Limit (Bytes)',
            name: 'memoryLimitBytes',
            xtype: 'numberfield',
            tooltipHtml: 'Maximum number of bytes of RAM that the script\'s container may use'
        }, {
            fieldLabel: 'Timeout (s)',
            name: 'timeoutSecs',
            xtype: 'numberfield',
            minValue: 1,
            tooltipHtml: 'The script\'s container will be forcibly stopped if it runs longer than this value'
        }, {
            fieldLabel: 'Run Automatically',
            name: 'runAutomatically',
            xtype: 'checkboxfield',
            uncheckedValue: false,
            inputValue: true,
            tooltipHtml: 'Check to run this script every time a new bag is added'
        }, {
            xtype: 'fieldcontainer',
            fieldLabel: 'Run Criteria',
            layout: 'border',
            flex: 0.5,
            tooltipHtml: 'If any criteria are added, at least one set must mach in order for the script to ' +
                         'automatically run.<br>If none are set, the script will always run.',
            items: [{
                xtype: 'grid',
                itemId: 'criteriaGrid',
                region: 'center',
                cls: 'x-form-trigger-wrap-default',
                store: {
                    model: 'BagDatabase.models.ScriptCriteria'
                },
                columns: [{
                    text: 'Directory', dataIndex: 'directory', flex: 1, editor: { field: { xtype: 'textfield' } }
                }, {
                    text: 'Filename', dataIndex: 'filename', flex: 1, editor: { field: { xtype: 'textfield' } }
                }, {
                    text: 'Message Types', dataIndex: 'messageTypes', flex: 1, editor: { field: { xtype: 'textfield' } }
                }, {
                    text: 'Topic Names', dataIndex: 'topicNames', flex: 1, editor: { field: { xtype: 'textfield' } }
                }],
                plugins: {
                    ptype: 'cellediting',
                    clicksToEdit: 1
                },
                saveCriteria: function(criteria) {
                    if (criteria.id) {
                        this.store.getById(criteria.id).set(criteria);
                    }
                    else {
                        this.store.add(criteria);
                    }
                    this.store.commitChanges();
                }
            }, {
                xtype: 'toolbar',
                region: 'east',
                vertical: true,
                items: [{
                    xtype: 'button',
                    text: 'Add',
                    iconCls: 'add-icon',
                    handler: function(button) {
                        var grid, win;
                        grid = button.up('window').down('#criteriaGrid');
                        win = Ext.create('BagDatabase.views.ScriptCriteriaWindow', {
                            scriptGrid: grid
                        });
                        win.show();
                    }
                }, '', {
                    xtype: 'button',
                    text: 'Edit',
                    iconCls: 'edit-icon',
                    handler: function(button) {
                        var grid, selection, win;
                        grid = button.up('window').down('#criteriaGrid');
                        selection = grid.getSelection();
                        if (selection.length > 0) {
                            win = Ext.create('BagDatabase.views.ScriptCriteriaWindow', {
                                scriptGrid: grid,
                                criteria: selection[0]
                            });
                            win.show();
                        }
                    }
                }, '', {
                    xtype: 'button',
                    text: 'Delete',
                    iconCls: 'delete-icon',
                    handler: function(button) {
                        var grid, selection;
                        grid = button.up('window').down('#criteriaGrid');
                        selection = grid.getSelection();
                        if (selection.length > 0)  {
                            grid.store.remove(selection[0]);
                        }
                    }
                }]
            }]
        }, {
            fieldLabel: 'Script',
            name: 'script',
            xtype: 'textareafield',
            allowBlank: false,
            fieldStyle: 'font-family: monospace;',
            flex: 1,
            tooltipHtml: 'An executable script'
        }],
        listeners: {
            afterRender: function() {
                var tips, fields;
                tips = [];
                fields = this.query('field');
                fields.push(this.down('fieldcontainer'));
                fields.forEach(function(field) {
                    if (field.config.tooltipHtml) {
                        tips.push(new Ext.tip.ToolTip({
                            target: field.labelEl,
                            width: 200,
                            html: field.config.tooltipHtml,
                            trackMouse: true
                        }));
                    }
                });
                this.tips = tips;
            },
            destroy: function() {
                this.tips = Ext.destroy(this.tips);
            }
        },
        buttons: [{
            text: 'Save',
            iconCls: 'script-save-icon',
            formBind: true,
            disabled: true,
            handler: function() {
                var form, params, store, win, criteria;
                form = this.up('form').getForm();
                win = this.up('window');
                store = win.store;
                if (form.isValid()) {
                    criteria = Ext.pluck(win.down('#criteriaGrid').getStore().getData().items, 'data');
                    params = {};
                    params['criteria'] = criteria;
                    headers = { 'Content-Type': "application/json" };
                    headers[csrfHeader] = csrfToken;
                    form.submit({
                        params: params,
                        headers: headers,
                        success: function() {
                            Ext.Msg.alert('Success', 'Script was saved.');
                            if (store) {
                                store.reload();
                            }
                            win.close();
                        },
                        failure: function() {
                            Ext.Msg.alert('Failure', 'Error saving script.');
                        }
                    });
                }
            }
        }]
    }],
    initComponent: function() {
        this.callParent(arguments);
        if (this.scriptId) {
            var scriptForm = this.down('#scriptForm');
            scriptForm.form.doAction('load', {
                url: 'scripts/get',
                method: 'GET',
                params: {
                    scriptId: this.scriptId
                },
                success: function(form, action) {
                    scriptForm.down('#criteriaGrid').store.loadData(action.result.data.criteria);
                }
            });
        }
    }
});