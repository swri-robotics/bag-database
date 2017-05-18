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


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Point;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name="bag_positions")
public class BagPosition {
    // The allocationSize here must be equal to the incrementBy value of the sequence in the database.
    // See db.changelog-1.6.yaml
    @Id
    @SequenceGenerator(name="bag_positions_id_seq", sequenceName="bag_positions_id_seq", allocationSize=100)
    @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="bag_positions_id_seq")
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="bagId")
    @JsonIgnore
    private Bag bag;

    private Point position;

    private Timestamp positionTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Bag getBag() {
        return bag;
    }

    public void setBag(Bag bag) {
        this.bag = bag;
    }

    @Transient
    public Double getLatitude() {
        return position == null ? null : position.getY();
    }

    @Transient
    public Double getLongitude() {
        return position == null ? null : position.getX();
    }

    @JsonIgnore
    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position = position;
    }

    public Timestamp getPositionTime() {
        return positionTime;
    }

    public void setPositionTime(Timestamp positionTime) {
        this.positionTime = positionTime;
    }
}
