/*
 * UniTime 3.2 (University Timetabling Application)
 * Copyright (C) 2008 - 2010, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
*/
package org.unitime.timetable.form;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;


/** 
* MyEclipse Struts
* Creation date: 02-18-2005
* 
* XDoclet definition:
* @struts:form name="roomFeatureListForm"
*/
public class RoomFeatureListForm extends ActionForm {

	private static final long serialVersionUID = 3256728385592768053L;
	/**
	 * 
	 */
	// --------------------------------------------------------- Instance Variables
	private Collection globalRoomFeatures;
	private Collection departmentRoomFeatures;
	private String deptCode;
	
	// --------------------------------------------------------- Methods

	public String getDeptCodeX() {
		return deptCode;
	}


	public void setDeptCodeX(String deptCode) {
		this.deptCode = deptCode;
	}


	/** 
	 * Method reset
	 * @param mapping
	 * @param request
	 */
	public void reset(ActionMapping mapping, HttpServletRequest request) {

		globalRoomFeatures = new ArrayList();
		departmentRoomFeatures = new ArrayList();
	}
	
	public Collection getGlobalRoomFeatures() {
		return globalRoomFeatures;
	}
	public void setGlobalRoomFeatures(Collection globalRoomFeatures) {
		this.globalRoomFeatures = globalRoomFeatures;
	}
	public Collection getDepartmentRoomFeatures() {
		return departmentRoomFeatures;
	}
	public void setDepartmentRoomFeatures(Collection departmentRoomFeatures) {
		this.departmentRoomFeatures = departmentRoomFeatures;
	}


	/* (non-Javadoc)
     * @see org.apache.struts.action.ActionForm#validate(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
     */
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        
        if(deptCode==null || deptCode.equalsIgnoreCase("")) {
        	errors.add("deptCode", 
                    new ActionMessage("errors.required", "Department") );
        }
       
        return errors;
    }
    
}
