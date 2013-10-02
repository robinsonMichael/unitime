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
package org.unitime.timetable.onlinesectioning.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.sf.cpsolver.coursett.Constants;
import net.sf.cpsolver.coursett.model.Placement;
import net.sf.cpsolver.coursett.model.TimeLocation;
import net.sf.cpsolver.ifs.util.DistanceMetric;
import net.sf.cpsolver.studentsct.model.Config;
import net.sf.cpsolver.studentsct.model.Course;
import net.sf.cpsolver.studentsct.model.Offering;
import net.sf.cpsolver.studentsct.model.Section;
import net.sf.cpsolver.studentsct.model.Student;
import net.sf.cpsolver.studentsct.model.Subpart;
import net.sf.cpsolver.studentsct.reservation.DummyReservation;
import net.sf.cpsolver.studentsct.reservation.GroupReservation;

import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.CourseReservation;
import org.unitime.timetable.model.CurriculumReservation;
import org.unitime.timetable.model.IndividualReservation;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Reservation;
import org.unitime.timetable.model.StudentGroupReservation;
import org.unitime.timetable.onlinesectioning.OnlineSectioningHelper;

public class XOffering implements Serializable {
    private static final long serialVersionUID = 1L;
	private Long iUniqueId = null;
    private String iName = null;
    private List<XConfig> iConfigs = new ArrayList<XConfig>();
    private List<XCourse> iCourses = new ArrayList<XCourse>();
    private List<XReservation> iReservations = new ArrayList<XReservation>();

    public XOffering() {
    }
    
    public XOffering(InstructionalOffering offering, OnlineSectioningHelper helper) {
    	iUniqueId = offering.getUniqueId();
    	iName = offering.getCourseName();
    	for (CourseOffering course: offering.getCourseOfferings())
    		iCourses.add(new XCourse(course, helper));
    	for (InstrOfferingConfig config: offering.getInstrOfferingConfigs())
    		iConfigs.add(new XConfig(config, helper));
        for (Reservation reservation: offering.getReservations()) {
        	if (reservation instanceof IndividualReservation) {
        		iReservations.add(new XIndividualReservation(this, (IndividualReservation)reservation));
        	} else if (reservation instanceof StudentGroupReservation) {
        		iReservations.add(new XGroupReservation(this, (StudentGroupReservation)reservation));
        	} else if (reservation instanceof CurriculumReservation) {
        		iReservations.add(new XCurriculumReservation(this, (CurriculumReservation)reservation));
        	} else if (reservation instanceof CourseReservation) {
        		iReservations.add(new XCourseReservation(this, (CourseReservation)reservation));
        	}
        }
        if (offering.isByReservationOnly())
        	iReservations.add(new XDummyReservation(this));
        
        Collections.sort(iConfigs);
    }
    
    public XOffering(Offering offering) {
    	iUniqueId = offering.getId();
    	iName = offering.getName();
    	for (Course course: offering.getCourses())
    		iCourses.add(new XCourse(course));
    	for (Config config: offering.getConfigs())
    		iConfigs.add(new XConfig(config));
    	for (net.sf.cpsolver.studentsct.reservation.Reservation reservation: offering.getReservations()) {
    		if (reservation instanceof net.sf.cpsolver.studentsct.reservation.IndividualReservation) {
        		iReservations.add(new XIndividualReservation((net.sf.cpsolver.studentsct.reservation.IndividualReservation)reservation));
        	} else if (reservation instanceof GroupReservation) {
        		iReservations.add(new XIndividualReservation((GroupReservation)reservation));
        	} else if (reservation instanceof net.sf.cpsolver.studentsct.reservation.CurriculumReservation) {
        		iReservations.add(new XCurriculumReservation((net.sf.cpsolver.studentsct.reservation.CurriculumReservation)reservation));
        	} else if (reservation instanceof net.sf.cpsolver.studentsct.reservation.CourseReservation) {
        		iReservations.add(new XCourseReservation((net.sf.cpsolver.studentsct.reservation.CourseReservation)reservation));
        	}  else if (reservation instanceof DummyReservation) {
        		iReservations.add(new XDummyReservation(this));
        	}
    	}
    }
    
    /** Offering id */
    public Long getOfferingId() {
        return iUniqueId;
    }

    /** Offering name */
    public String getName() {
        return iName;
    }

    /** Possible configurations */
    public List<XConfig> getConfigs() {
        return iConfigs;
    }

    /**
     * List of courses. One instructional offering can contain multiple courses
     * (names under which it is offered)
     */
    public List<XCourse> getCourses() {
        return iCourses;
    }
    
    /** Course of this offering with the given id */
    public XCourse getCourse(Long courseId) {
    	for (XCourse course: getCourses())
    		if (course.getCourseId().equals(courseId)) return course;
    	return null;
    }
    
    /** Course of this offering with the given id */
    public XCourse getCourse(XCourseId courseId) {
    	for (XCourse course: getCourses())
    		if (course.getCourseId().equals(courseId.getCourseId())) return course;
    	return null;
    }
    
    /** List of sections of the given enrollment */
    public List<XSection> getSections(XEnrollment enrollment) {
    	List<XSection> sections = new ArrayList<XSection>();
        for (XConfig config : getConfigs()) {
            for (XSubpart subpart : config.getSubparts()) {
                for (XSection section : subpart.getSections()) {
                    if (enrollment.getSectionIds().contains(section.getSectionId()))
                    	sections.add(section);
                }
            }
        }
        return sections;
    }

    /**
     * Return section of the given id, if it is part of one of this offering
     * configurations.
     */
    public XSection getSection(Long sectionId) {
    	if (sectionId == null) return null;
        for (XConfig config : getConfigs()) {
            for (XSubpart subpart : config.getSubparts()) {
                for (XSection section : subpart.getSections()) {
                    if (section.getSectionId().equals(sectionId))
                        return section;
                }
            }
        }
        return null;
    }
    
    /**
     * Return subpart of the given id, if it is part of this offering configuraions.
     */
    public XSubpart getSubpart(Long subpartId) {
    	if (subpartId == null) return null;
        for (XConfig config : getConfigs()) {
            for (XSubpart subpart : config.getSubparts()) {
                if (subpart.getSubpartId().equals(subpartId))
                	return subpart;
            }
        }
        return null;
    }
    
    /**
     * Return config of the given id, if it is part of this offering configuraions.
     */
    public XConfig getConfig(Long configId) {
    	if (configId == null) return null;
        for (XConfig config : getConfigs()) {
            if (config.getConfigId().equals(configId))
            	return config;
        }
        return null;
    }
    
    /** Return set of instructional types, union over all configurations. */
    public Set<String> getInstructionalTypes() {
        Set<String> instructionalTypes = new HashSet<String>();
        for (XConfig config : getConfigs()) {
            for (XSubpart subpart : config.getSubparts()) {
                instructionalTypes.add(subpart.getInstructionalType());
            }
        }
        return instructionalTypes;
    }

    /**
     * Return list of all subparts of the given isntructional type for this
     * offering.
     */
    public Set<XSubpart> getSubparts(String instructionalType) {
        Set<XSubpart> subparts = new HashSet<XSubpart>();
        for (XConfig config : getConfigs()) {
            for (XSubpart subpart : config.getSubparts()) {
                if (instructionalType.equals(subpart.getInstructionalType()))
                    subparts.add(subpart);
            }
        }
        return subparts;
    }

    @Override
    public String toString() {
        return iName;
    }
    
    /** Reservations associated with this offering */
    public List<XReservation> getReservations() { return iReservations; }
    
    /**
     * Get reservations that require this section
     */
    public List<XReservation> getSectionReservations(Long sectionId) {
    	List<XReservation> ret = new ArrayList<XReservation>();
    	for (XReservation reservation: iReservations)
    		for (Set<Long> sectionIds: reservation.getSections().values())
    			if (sectionIds.contains(sectionId))
    				ret.add(reservation);
        return ret;
    }
    
    /**
     * Get reservations that require this config
     */
    public List<XReservation> getConfigReservations(Long configId) {
    	List<XReservation> ret = new ArrayList<XReservation>();
    	for (XReservation reservation: iReservations)
    		if (reservation.getConfigsIds().contains(configId))
    			ret.add(reservation);
        return ret;
    }
    
    public int getUnreservedSectionSpace(Long sectionId, XEnrollments enrollments) {
    	XSection section = getSection(sectionId);
        // section is unlimited -> there is unreserved space unless there is an unlimited reservation too 
        // (in which case there is no unreserved space)
        if (section.getLimit() < 0) {
            // exclude reservations that are not directly set on this section
            for (XReservation r: getSectionReservations(sectionId)) {
                // ignore expired reservations
                if (r.isExpired()) continue;
                // there is an unlimited reservation -> no unreserved space
                if (r.getLimit() < 0) return 0;
            }
            return Integer.MAX_VALUE;
        }
        
        int available = section.getLimit() - enrollments.countEnrollmentsForSection(sectionId);
        // exclude reservations that are not directly set on this section
        for (XReservation r: getSectionReservations(sectionId)) {
            // ignore expired reservations
            if (r.isExpired()) continue;
            // unlimited reservation -> all the space is reserved
            if (r.getLimit() < 0.0) return 0;
            // compute space that can be potentially taken by this reservation
            int reserved = r.getReservedAvailableSpace(enrollments);
            // deduct the space from available space
            available -= Math.max(0, reserved);
        }
        
        return available;
    }
    
    public int getUnreservedConfigSpace(Long configId, XEnrollments enrollments) {
    	XConfig config = getConfig(configId);
        // configuration is unlimited -> there is unreserved space unless there is an unlimited reservation too 
        // (in which case there is no unreserved space)
        if (config.getLimit() < 0) {
            // exclude reservations that are not directly set on this section
            for (XReservation r: getConfigReservations(configId)) {
                // ignore expired reservations
                if (r.isExpired()) continue;
                // there is an unlimited reservation -> no unreserved space
                if (r.getLimit() < 0) return 0;
            }
            return Integer.MAX_VALUE;
        }
        
        int available = config.getLimit() - enrollments.countEnrollmentsForConfig(configId);
        // exclude reservations that are not directly set on this section
        for (XReservation r: getConfigReservations(configId)) {
            // ignore expired reservations
            if (r.isExpired()) continue;
            // unlimited reservation -> all the space is reserved
            if (r.getLimit() < 0) return 0;
            // compute space that can be potentially taken by this reservation
            double reserved = r.getReservedAvailableSpace(enrollments);
            // deduct the space from available space
            available -= Math.max(0, reserved);
        }
        
        return available;
    }
    
    public int getUnreservedSpace(XEnrollments enrollments) {
        // compute available space
        int available = 0;
        for (XConfig config: getConfigs()) {
            available += config.getLimit() - enrollments.countEnrollmentsForConfig(config.getConfigId());
            // offering is unlimited -> there is unreserved space unless there is an unlimited reservation too 
            // (in which case there is no unreserved space)
            if (config.getLimit() < 0) {
                for (XReservation r: getReservations()) {
                    // ignore expired reservations
                    if (r.isExpired()) continue;
                    // there is an unlimited reservation -> no unreserved space
                    if (r.getLimit() < 0) return 0;
                }
                return Integer.MAX_VALUE;
            }
        }
        
        // compute reserved space (out of the available space)
        int reserved = 0;
        for (XReservation r: getReservations()) {
            // ignore expired reservations
            if (r.isExpired()) continue;
            // unlimited reservation -> no unreserved space
            if (r.getLimit() < 0) return 0;
            reserved += Math.max(0.0,  r.getReservedAvailableSpace(enrollments));
        }
        
        return available - reserved;
    }
    
    /** True if there are reservations for this offering */
    public boolean hasReservations() { return !iReservations.isEmpty(); }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof XOffering)) return false;
        return getOfferingId().equals(((XOffering)o).getOfferingId());
    }
    
    @Override
    public int hashCode() {
        return (int) (getOfferingId() ^ (getOfferingId() >>> 32));
    }
    

    public XReservationId guessReservation(List<XEnrollment> other, XStudent student, XEnrollment enrollment) {
    	if (!enrollment.getOfferingId().equals(getOfferingId())) return null;
    	
    	Set<XReservation> reservations = new TreeSet<XReservation>();
    	boolean mustBeUsed = false;
    	for (XReservation reservation: getReservations()) {
    		if (reservation.isApplicable(student)) {
    			if (!mustBeUsed && reservation.mustBeUsed()) { reservations.clear(); mustBeUsed = true; }
    			if (mustBeUsed && !reservation.mustBeUsed()) continue;
    			reservations.add(reservation);
    		}
    	}
    	if (reservations.isEmpty()) return null;
    	
    	List<XSection> sections = getSections(enrollment);
    	for (XReservation reservation: reservations) {
    		if (reservation.isIncluded(enrollment.getConfigId(), sections)) {
    			if (reservation.getLimit() < 0.0 || other == null)
    				return new XReservationId(reservation.getType(), getOfferingId(), reservation.getReservationId());
    			int used = 0;
    			for (XEnrollment e: other)
    				if (!enrollment.getStudentId().equals(e.getStudentId()) && reservation.equals(e.getReservation())) used ++;
    			if (used < reservation.getLimit())
    				return new XReservationId(reservation.getType(), getOfferingId(), reservation.getReservationId());
    		}
    	}
    	return null;
    }
    
	public static class SimpleReservation extends net.sf.cpsolver.studentsct.reservation.Reservation {
		private XReservationType iType;
		private int iPriority;
		private boolean iOver;
		private int iLimit;
		private boolean iApply;
		private boolean iMustUse;
		private boolean iAllowOverlap;
		
		public SimpleReservation(XReservationType type, long id, Offering offering, int priority, boolean over, int limit, boolean apply, boolean mustUse, boolean allowOverlap, boolean expired) {
			super(id, offering);
			iType = type;
			iPriority = priority;
			iOver = over;
			iLimit = limit;
			iApply = apply;
			iMustUse = mustUse;
			iAllowOverlap = allowOverlap;
			setExpired(expired);
		}
		
		public XReservationType getType() {
			return iType;
		}
		
		@Override
		public boolean canAssignOverLimit() {
			return iOver;
		}

		@Override
		public boolean mustBeUsed() {
			return iMustUse;
		}
		
		@Override
		public double getReservationLimit() {
			return iLimit;
		}

		@Override
		public int getPriority() {
			return iPriority;
		}

		@Override
		public boolean isApplicable(Student student) {
			return iApply;
		}

		@Override
		public boolean isAllowOverlap() {
			return iAllowOverlap;
		}
	}
	
	public int distance(DistanceMetric m, Section s1, Section s2) {
        if (s1.getPlacement()==null || s2.getPlacement()==null) return 0;
        TimeLocation t1 = s1.getTime();
        TimeLocation t2 = s2.getTime();
        if (!t1.shareDays(t2) || !t1.shareWeeks(t2)) return 0;
        int a1 = t1.getStartSlot(), a2 = t2.getStartSlot();
        if (m.doComputeDistanceConflictsBetweenNonBTBClasses()) {
        	if (a1 + t1.getNrSlotsPerMeeting() <= a2) {
        		int dist = Placement.getDistanceInMinutes(m, s1.getPlacement(), s2.getPlacement());
        		if (dist > t1.getBreakTime() + Constants.SLOT_LENGTH_MIN * (a2 - a1 - t1.getLength()))
        			return dist;
        	}
        } else {
        	if (a1+t1.getNrSlotsPerMeeting()==a2)
        		return Placement.getDistanceInMinutes(m, s1.getPlacement(), s2.getPlacement());
        }
        return 0;
    }	
	
	public static class EnrollmentSectionComparator implements Comparator<Section> {
	    public boolean isParent(Section s1, Section s2) {
			Section p1 = s1.getParent();
			if (p1==null) return false;
			if (p1.equals(s2)) return true;
			return isParent(p1, s2);
		}

		public int compare(Section a, Section b) {
			if (isParent(a, b)) return 1;
	        if (isParent(b, a)) return -1;

	        int cmp = a.getSubpart().getInstructionalType().compareToIgnoreCase(b.getSubpart().getInstructionalType());
			if (cmp != 0) return cmp;
			
			return Double.compare(a.getId(), b.getId());
		}
	}

    public Course toCourse(Long courseId, XStudent student, XExpectations expectations, Collection<XDistribution> distributions, XEnrollments enrollments) {
		Offering clonedOffering = new Offering(getOfferingId(), getName());
		XCourse course = getCourse(courseId);
		int courseLimit = course.getLimit();
		if (courseLimit >= 0) {
			courseLimit -= enrollments.countEnrollmentsForCourse(course.getCourseId());
			if (courseLimit < 0) courseLimit = 0;
			for (XEnrollment enrollment: enrollments.getEnrollmentsForCourse(course.getCourseId())) {
				if (enrollment.getStudentId().equals(student.getStudentId())) { courseLimit++; break; }
			}
		}
		Course clonedCourse = new Course(course.getCourseId(), course.getSubjectArea(), course.getCourseNumber(), clonedOffering, courseLimit, course.getProjected());
		clonedCourse.setNote(course.getNote());
		Hashtable<Long, Config> configs = new Hashtable<Long, Config>();
		Hashtable<Long, Subpart> subparts = new Hashtable<Long, Subpart>();
		Hashtable<Long, Section> sections = new Hashtable<Long, Section>();
		for (XConfig config: getConfigs()) {
			int configLimit = config.getLimit();
			if (configLimit >= 0) {
				configLimit -= enrollments.countEnrollmentsForConfig(config.getConfigId());
				if (configLimit < 0) configLimit = 0;
				for (XEnrollment enrollment: enrollments.getEnrollmentsForConfig(config.getConfigId())) {
					if (enrollment.getStudentId().equals(student.getStudentId())) { configLimit++; break; }
				}
			}
			Config clonedConfig = new Config(config.getConfigId(), configLimit, config.getName(), clonedOffering);
			configs.put(config.getConfigId(), clonedConfig);
			for (XSubpart subpart: config.getSubparts()) {
				Subpart clonedSubpart = new Subpart(subpart.getSubpartId(), subpart.getInstructionalType(), subpart.getName(), clonedConfig,
						(subpart.getParentId() == null ? null: subparts.get(subpart.getParentId())));
				clonedSubpart.setAllowOverlap(subpart.isAllowOverlap());
				clonedSubpart.setCredit(subpart.getCredit());
				subparts.put(subpart.getSubpartId(), clonedSubpart);
				for (XSection section: subpart.getSections()) {
					int limit = section.getLimit();
					if (limit >= 0) {
						// limited section, deduct enrollments
						limit -= enrollments.countEnrollmentsForSection(section.getSectionId());
						if (limit < 0) limit = 0; // over-enrolled, but not unlimited
						for (XEnrollment enrollment: enrollments.getEnrollmentsForSection(section.getSectionId()))
							if (enrollment.getStudentId().equals(student.getStudentId())) { limit++; break; }
					}
                    String instructorIds = "";
                    String instructorNames = "";
                    for (XInstructor instructor: section.getInstructors()) {
                    	if (!instructorIds.isEmpty()) {
                    		instructorIds += ":"; instructorNames += ":";
                    	}
                    	instructorIds += instructor.getIntructorId().toString();
                    	instructorNames += instructor.getName() + "|"  + (instructor.getEmail() == null ? "" : instructor.getEmail());
                    }
					Section clonedSection = new Section(section.getSectionId(), limit,
							section.getName(), clonedSubpart, section.toPlacement(),
							instructorIds, instructorNames,
							(section.getParentId() == null ? null : sections.get(section.getParentId())));
					clonedSection.setName(-1l, section.getName(-1l));
					clonedSection.setNote(section.getNote());
					clonedSection.setSpaceExpected(expectations == null ? 0.0 : expectations.getExpectedSpace(section.getSectionId()));
					if (distributions != null)
						for (XDistribution distribution: distributions)
							if (distribution.getDistributionType() == XDistributionType.IngoreConflicts && distribution.hasSection(section.getSectionId()))
								for (Long id: distribution.getSectionIds())
									if (!id.equals(section.getSectionId()))
										clonedSection.addIgnoreConflictWith(id);
			        if (limit > 0) {
			        	double available = Math.round(clonedSection.getSpaceExpected() - limit);
						clonedSection.setPenalty(available / section.getLimit());
			        }
					sections.put(section.getSectionId(), clonedSection);
				}
			}
		}
		for (XReservation reservation: getReservations()) {
			int reservationLimit = (int)Math.round(reservation.getLimit());
			if (reservationLimit >= 0) {
				reservationLimit -= enrollments.countEnrollmentsForReservation(reservation.getReservationId());
				if (reservationLimit < 0) reservationLimit = 0;
				for (XEnrollment enrollment: enrollments.getEnrollmentsForReservation(reservation.getReservationId())) {
					if (enrollment.getStudentId().equals(student.getStudentId())) { reservationLimit++; break; }
				}
				if (reservationLimit <= 0) continue;
			}
			boolean applicable = reservation.isApplicable(student);
			if (reservation instanceof XCourseReservation)
				applicable = ((XCourseReservation)reservation).getCourseId().equals(courseId);
			if (reservation instanceof XDummyReservation) {
				// Ignore by reservation only flag (dummy reservation) when the student is already enrolled in the course
				for (XEnrollment enrollment: enrollments.getEnrollmentsForCourse(courseId))
					if (enrollment.getStudentId().equals(student.getStudentId())) { applicable = true; break; }
			}
			net.sf.cpsolver.studentsct.reservation.Reservation clonedReservation = new SimpleReservation(reservation.getType(), reservation.getReservationId(), clonedOffering,
					reservation.getPriority(), reservation.canAssignOverLimit(), reservationLimit, 
					applicable, reservation.mustBeUsed(), reservation.isAllowOverlap(), reservation.isExpired());
			for (Long configId: reservation.getConfigsIds())
				clonedReservation.addConfig(configs.get(configId));
			for (Map.Entry<Long, Set<Long>> entry: reservation.getSections().entrySet()) {
				Set<Section> clonedSections = new HashSet<Section>();
				for (Long sectionId: entry.getValue())
					clonedSections.add(sections.get(sectionId));
				clonedReservation.getSections().put(subparts.get(entry.getKey()), clonedSections);
			}
		}
		return clonedCourse;
    }
}
