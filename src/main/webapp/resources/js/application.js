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

/**
 * Starts the bag database application.
 */
function startApplication() {
    Ext.application({
        name: 'Bag Database',
        requires: [ 'BagDatabase.views.BagDatabaseViewport' ],
        autoCreateViewport: 'BagDatabase.views.BagDatabaseViewport'
    });
}

bagGridDateRenderer = Ext.util.Format.dateRenderer('n/j/Y H:i:s');

Ext.onReady(function() {
    // Set up our state provider before we start the app so we can reliably
    // restore our previous state.
    Ext.state.Manager.setProvider(new Ext.state.LocalStorageProvider());
    try {
        startApplication();
    }
    catch (e) {
        if (loadCompressed && e.msg.match('Ext.Loader is not enabled')) {
            // If the app fails to start and it is because we were set to load in non-debug
            // mode but no classes were available, that probably means we're running in
            // an and offline test environment (like "mvn tomcat7:run") and the compressed.js
            // file is not on the classpath.  In order so that we can reliably run, enable
            // the loader and try requiring a class again; if the class successfully loads,
            // that will cause the application to start.
            Ext.Loader.setConfig({enabled: true});
            Ext.Loader.setPath('BagDatabase', 'resources/js');
            console.log('Could not load compressed application; retrying in debug mode.')
            try {
                Ext.require('BagDatabase.views.BagDatabaseViewport');
            }
            catch(e2) {
                console.error('Error loading application in debug mode:');
                console.error(e);
            }
        }
        else {
            console.error('Failed to load Bag Database:');
            console.error(e);
        }
    }
});
