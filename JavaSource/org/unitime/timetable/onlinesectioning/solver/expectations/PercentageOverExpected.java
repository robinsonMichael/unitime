/*
 * UniTime 3.5 (University Timetabling Application)
 * Copyright (C) 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.onlinesectioning.solver.expectations;

import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.studentsct.model.Request;
import net.sf.cpsolver.studentsct.model.Section;

/**
 * @author Tomas Muller
 */
public class PercentageOverExpected implements OverExpectedCriterion {
	private Double iPercentage = null;
	
	public PercentageOverExpected(DataProperties config) {
		iPercentage = config.getPropertyDouble("OverExpected.Percentage", iPercentage);
	}
	
	public PercentageOverExpected(Double percentage) {
		iPercentage = percentage;
	}
	
	public PercentageOverExpected() {
		this((Double)null);
	}
	
	public double getPercentage() {
		return iPercentage == null ? 1.0 : iPercentage;
	}

	@Override
	public double getOverExpected(Section section, Request request) {
		if (section.getLimit() <= 0) return 0.0; // ignore unlimited & not available
		
		double expected = getPercentage() * section.getSpaceExpected();
		double enrolled = section.getEnrollmentWeight(request) + request.getWeight();
		double limit = section.getLimit();
		int subparts = section.getSubpart().getConfig().getSubparts().size();
		
		return expected + enrolled > limit ? 1.0 / subparts : 0.0;
	}
	
	@Override
	public String toString() {
		return "perc(" + getPercentage() + ")";
	}

}
