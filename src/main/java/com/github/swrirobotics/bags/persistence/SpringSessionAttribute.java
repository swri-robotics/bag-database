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

package com.github.swrirobotics.bags.persistence;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Arrays;

/**
 * This table is designed to model the table used by Spring Session to store user
 * session attributess; see the documentation at
 * http://docs.spring.io/spring-session/docs/1.2.x/reference/html5/#api-jdbcoperationssessionrepository-storage .
 */
@Entity
@Table(name = "SPRING_SESSION_ATTRIBUTES", indexes = @Index(columnList = "session_id"))
public class SpringSessionAttribute implements Serializable {
    private static final long serialVersionUID = 8877915121294599668L;

    @ManyToOne
    @JoinColumn(name="session_id")
    @Id
    public SpringSession session;
    @Column(length = 200)
    @Id
    public String attribute_name;
    // Postgres will create this field as a "bytea", which does not take a length,
    // but hsqldb will create it as a longvarchar, and the default length is not
    // long enough to store Spring's Security Context info.
    @Basic
    @Column(length = 10000)
    public byte[] attribute_bytes; // TODO Make this work in hsql

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SpringSessionAttribute that = (SpringSessionAttribute) o;

        if (session != null ? !session.equals(that.session) : that.session != null) {
            return false;
        }
        if (attribute_name != null ? !attribute_name.equals(that.attribute_name) : that.attribute_name != null) {
            return false;
        }
        return attribute_bytes != null ? Arrays.equals(that.attribute_bytes, attribute_bytes) : that.attribute_bytes == null;

    }

    @Override
    public int hashCode() {
        int result = session != null ? session.hashCode() : 0;
        result = 31 * result + (attribute_name != null ? attribute_name.hashCode() : 0);
        result = 31 * result + (attribute_bytes != null ? Arrays.hashCode(attribute_bytes) : 0);
        return result;
    }
}
