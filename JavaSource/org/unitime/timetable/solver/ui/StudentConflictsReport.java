/*
 * UniTime 3.1 (University Timetabling Application)
 * Copyright (C) 2008, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/
package org.unitime.timetable.solver.ui;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import net.sf.cpsolver.coursett.constraint.JenrlConstraint;
import net.sf.cpsolver.coursett.model.Lecture;
import net.sf.cpsolver.coursett.model.TimetableModel;
import net.sf.cpsolver.ifs.solver.Solver;

/**
 * @author Tomas Muller
 */
public class StudentConflictsReport implements Serializable {

	private static final long serialVersionUID = 1L;
	private HashSet iGroups = new HashSet();

	public StudentConflictsReport(Solver solver) {
		TimetableModel model = (TimetableModel)solver.currentSolution().getModel();
		for (JenrlConstraint jenrl: model.getJenrlConstraints()) {
			if (jenrl.isInConflict())
				iGroups.add(new JenrlInfo(solver, jenrl));
		}
		for (Lecture lecture: model.assignedVariables()) {
			iGroups.addAll(JenrlInfo.getCommitedJenrlInfos(solver, lecture).values());
		}
		for (Lecture lecture: model.constantVariables()) {
			if (lecture.getAssignment() != null)
				iGroups.addAll(JenrlInfo.getCommitedJenrlInfos(solver, lecture).values());
		}
	}
	
	public Set getGroups() {
		return iGroups;
	}
}
