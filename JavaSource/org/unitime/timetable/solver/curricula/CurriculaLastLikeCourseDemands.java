/*
 * UniTime 3.2 - 3.5 (University Timetabling Application)
 * Copyright (C) 2010 - 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.solver.curricula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cpsolver.ifs.assignment.Assignment;
import org.cpsolver.ifs.assignment.DefaultSingleAssignment;
import org.cpsolver.ifs.model.Constraint;
import org.cpsolver.ifs.solution.Solution;
import org.cpsolver.ifs.util.DataProperties;
import org.cpsolver.ifs.util.IdGenerator;
import org.cpsolver.ifs.util.Progress;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.hibernate.Session;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.Curriculum;
import org.unitime.timetable.model.CurriculumClassification;
import org.unitime.timetable.model.CurriculumCourse;
import org.unitime.timetable.model.CurriculumCourseGroup;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.PosMajor;
import org.unitime.timetable.solver.curricula.students.CurCourse;
import org.unitime.timetable.solver.curricula.students.CurModel;
import org.unitime.timetable.solver.curricula.students.CurStudent;
import org.unitime.timetable.solver.curricula.students.CurValue;
import org.unitime.timetable.solver.curricula.students.CurVariable;

/**
 * @author Tomas Muller
 */
public class CurriculaLastLikeCourseDemands implements StudentCourseDemands {
	private static Log sLog = LogFactory.getLog(CurriculaLastLikeCourseDemands.class);

	private ProjectedStudentCourseDemands iProjectedDemands;
	private IdGenerator iLastStudentId = new IdGenerator();
	private Hashtable<Long, Set<WeightedStudentId>> iDemands = new Hashtable<Long, Set<WeightedStudentId>>();
	private Hashtable<Long, Set<WeightedCourseOffering>> iStudentRequests = new Hashtable<Long, Set<WeightedCourseOffering>>();
	private Hashtable<Long, Hashtable<String, Set<String>>> iLoadedCurricula = new Hashtable<Long,Hashtable<String, Set<String>>>();
	private Hashtable<Long, Hashtable<Long, Double>> iEnrollmentPriorities = new Hashtable<Long, Hashtable<Long, Double>>();
	private HashSet<Long> iCheckedCourses = new HashSet<Long>();
	private boolean iIncludeOtherStudents = true;
	private boolean iSetStudentCourseLimits = false;
	private CurriculumEnrollmentPriorityProvider iEnrollmentPriorityProvider = null;
	private DataProperties iProperties = null;

	public CurriculaLastLikeCourseDemands(DataProperties config) {
		iProperties = config;
		iProjectedDemands = new ProjectedStudentCourseDemands(config);
		iIncludeOtherStudents = config.getPropertyBoolean("CurriculaCourseDemands.IncludeOtherStudents", iIncludeOtherStudents);
		iSetStudentCourseLimits = config.getPropertyBoolean("CurriculaCourseDemands.SetStudentCourseLimits", iSetStudentCourseLimits);
		iEnrollmentPriorityProvider = new DefaultCurriculumEnrollmentPriorityProvider(config);
		if (config.getProperty("CurriculaCourseDemands.CurriculumEnrollmentPriorityProvider") != null) {
			try {
				iEnrollmentPriorityProvider = (CurriculumEnrollmentPriorityProvider)Class.forName(
						config.getProperty("CurriculaCourseDemands.CurriculumEnrollmentPriorityProvider"))
						.getConstructor(DataProperties.class).newInstance(config);
			} catch (Exception e) {
				sLog.error("Failed to use custom enrollment priority provider: " + e.getMessage(), e);
			}
		}
	}

	@Override
	public void init(Session hibSession, Progress progress,
			org.unitime.timetable.model.Session session,
			Collection<InstructionalOffering> offerings) {

		iProjectedDemands.init(hibSession, progress, session, offerings);
		
		List<Curriculum> curricula = null;
		if (offerings != null && offerings.size() <= 1000) {
			String courses = "";
			int nrCourses = 0;
			for (InstructionalOffering offering: offerings)
				for (CourseOffering course: offering.getCourseOfferings()) {
					if (!courses.isEmpty()) courses += ",";
					courses += course.getUniqueId();
					nrCourses++;
				}
			if (nrCourses <= 1000) {
				curricula = hibSession.createQuery(
						"select distinct c from CurriculumCourse cc inner join cc.classification.curriculum c where " +
						"c.academicArea.session.uniqueId = :sessionId and cc.course.uniqueId in (" + courses + ")")
						.setLong("sessionId", session.getUniqueId()).list();
			}
		}
		if (curricula == null) {
			curricula = hibSession.createQuery(
					"select c from Curriculum c where c.academicArea.session.uniqueId = :sessionId")
					.setLong("sessionId", session.getUniqueId()).list();
		}

		List<Initialization> inits = new ArrayList<Initialization>();
		for (Curriculum curriculum: curricula) {
			Hashtable<String, Hashtable<CourseOffering, Set<WeightedStudentId>>> lastLike = loadClasfCourseMajor2ll(hibSession, curriculum);
			for (CurriculumClassification clasf: curriculum.getClassifications()) {
				inits.add(new Initialization(clasf, lastLike.get(clasf.getAcademicClassification().getCode())));
			}
		}
		new ParallelInitialization(
				"Loading curricula",
				iProperties.getPropertyInt("CurriculaCourseDemands.NrThreads", 1),
				inits).execute(hibSession, progress);
	}
	
	private Hashtable<String, Hashtable<CourseOffering, Set<WeightedStudentId>>> loadClasfCourseMajor2ll(org.hibernate.Session hibSession, Curriculum curriculum) {
		String majorCodes = "";
		for (PosMajor major: curriculum.getMajors()) {
			if (!majorCodes.isEmpty()) majorCodes += ",";
			majorCodes += "'" + major.getCode() + "'";
		}
		
		Hashtable<String, Hashtable<CourseOffering, Set<WeightedStudentId>>> clasf2courseLl = new Hashtable<String, Hashtable<CourseOffering, Set<WeightedStudentId>>>();
		
		for (Object[] o : (List<Object[]>)hibSession.createQuery(
				"select f.code, co, m.code, s.uniqueId " +
				"from LastLikeCourseDemand x inner join x.student s inner join s.academicAreaClassifications a inner join a.academicClassification f " + 
				"inner join s.posMajors m, CourseOffering co where " +
				"x.subjectArea.session.uniqueId = :sessionId and "+
				"a.academicArea.academicAreaAbbreviation = :acadAbbv " + 
				(majorCodes.isEmpty() ? "" : "and m.code in (" + majorCodes + ") ") +
				"and co.subjectArea.uniqueId = x.subjectArea.uniqueId and " +
				"((x.coursePermId is not null and co.permId=x.coursePermId) or (x.coursePermId is null and co.courseNbr=x.courseNbr))")
				.setLong("sessionId", curriculum.getDepartment().getSession().getUniqueId())
				.setString("acadAbbv", curriculum.getAcademicArea().getAcademicAreaAbbreviation())
				.setCacheable(true).list()) {
			String clasfCode = (String)o[0];
			CourseOffering course = (CourseOffering)o[1];
			String majorCode = (String)o[2];
			Long studentId = (Long)o[3];
			
			WeightedStudentId student = new WeightedStudentId(studentId, iProjectedDemands.getProjection(curriculum.getAcademicArea().getAcademicAreaAbbreviation(), clasfCode, majorCode));
			student.setStats(curriculum.getAcademicArea().getAcademicAreaAbbreviation(), clasfCode, majorCode);
			student.setCurriculum(curriculum.getAbbv());
			
			Hashtable<CourseOffering, Set<WeightedStudentId>> course2ll = clasf2courseLl.get(clasfCode);
			if (course2ll == null) {
				course2ll = new Hashtable<CourseOffering, Set<WeightedStudentId>>();
				clasf2courseLl.put(clasfCode, course2ll);
			}
			Set<WeightedStudentId> students = course2ll.get(course);
			if (students == null) {
				students = new HashSet<WeightedStudentId>();
				course2ll.put(course, students);
			}
			students.add(student);
		}
		
		return clasf2courseLl;
	}
		
	protected String getCacheName() {
		return "curriculum-lastlike-demands";
	}

	protected void computeTargetShare(CurriculumClassification clasf, CurModel model) {
		for (CurriculumCourse c1: clasf.getCourses()) {
			double x1 = clasf.getNrStudents() * c1.getPercShare();
			Set<CurriculumCourse>[] group = new HashSet[] { new HashSet<CurriculumCourse>(), new HashSet<CurriculumCourse>()};
			Queue<CurriculumCourse> queue = new LinkedList<CurriculumCourse>();
			queue.add(c1);
			Set<CurriculumCourseGroup> done = new HashSet<CurriculumCourseGroup>();
			while (!queue.isEmpty()) {
				CurriculumCourse c = queue.poll();
				for (CurriculumCourseGroup g: c.getGroups())
					if (done.add(g))
						for (CurriculumCourse x: clasf.getCourses())
							if (!x.equals(c) && !x.equals(c1) && x.getGroups().contains(g) && group[group[0].contains(c) ? 0 : g.getType()].add(x))
								queue.add(x);
			}
			for (CurriculumCourse c2: clasf.getCourses()) {
				double x2 = clasf.getNrStudents() * c2.getPercShare();
				if (c1.getUniqueId() >= c2.getUniqueId()) continue;
				double share = 0;
				Set<WeightedStudentId> s1 = iProjectedDemands.getDemands(c1.getCourse());
				Set<WeightedStudentId> s2 = iProjectedDemands.getDemands(c2.getCourse());
				double sharedStudents = 0, lastLike = 0;
				if (s1 != null && !s1.isEmpty() && s2 != null && !s2.isEmpty()) {
					for (WeightedStudentId s: s1) {
						if (s.match(clasf)) {
							lastLike += s.getWeight();
							if (s2.contains(s)) sharedStudents += s.getWeight();
						}
					}
				}
				if (lastLike > 0) {
					double requested = c1.getPercShare() * clasf.getNrStudents();
					share = (requested / lastLike) * sharedStudents;
				} else {
					share = c1.getPercShare() * c2.getPercShare() * clasf.getNrStudents();
				}
				boolean opt = group[0].contains(c2);
				boolean req = !opt && group[1].contains(c2);
				model.setTargetShare(c1.getCourse().getUniqueId(), c2.getCourse().getUniqueId(), opt ? 0.0 : req ? Math.min(x1, x2) : share, false);
			}
		}
	}

	@Override
	public Set<WeightedCourseOffering> getCourses(Long studentId) {
		Set<WeightedCourseOffering> courses = iStudentRequests.get(studentId);
		if (iIncludeOtherStudents && studentId >= 0 && courses == null)
			return iProjectedDemands.getCourses(studentId);
		return iStudentRequests.get(studentId);
	}
	

	@Override
	public Set<WeightedStudentId> getDemands(CourseOffering course) {
		if (iDemands.isEmpty()) return iProjectedDemands.getDemands(course);
		Set<WeightedStudentId> demands = iDemands.get(course.getUniqueId());
		if (!iIncludeOtherStudents) return demands;
		if (demands == null) {
			demands = new HashSet<WeightedStudentId>();
			iDemands.put(course.getUniqueId(), demands);
		}
		if (iCheckedCourses.add(course.getUniqueId())) {
			int was = demands.size();
			Hashtable<String,Set<String>> curricula = iLoadedCurricula.get(course.getUniqueId());
			Set<WeightedStudentId> other = iProjectedDemands.getDemands(course);
			if (other == null) {
				sLog.debug(course.getCourseName() + " has no students.");	
			} else {
				if (curricula == null || curricula.isEmpty()) {
					demands.addAll(other);
				} else {
					for (WeightedStudentId student: other) {
						if (student.getArea() == null) continue; // ignore students w/o academic area
						Set<String> majors = curricula.get(student.getArea());
						if (majors != null && majors.contains("")) continue; // all majors
						if (majors == null || (student.getMajor() != null && !majors.contains(student.getMajor())))
							demands.add(student);
					}
				}
				if (demands.size() > was)
					sLog.debug(course.getCourseName() + " has " + (demands.size() - was) + " other students (besides of the " + was + " curriculum students).");
			}
		}
		return demands;
	}
	
	@Override
	public boolean canUseStudentClassEnrollmentsAsSolution() {
		return false;
	}

	@Override
	public boolean isMakingUpStudents() {
		return false; // most students should be last-like students
	}

	@Override
	public boolean isWeightStudentsToFillUpOffering() {
		return false;
	}

	@Override
	public Double getEnrollmentPriority(Long studentId, Long courseId) {
		Hashtable<Long, Double> priorities = iEnrollmentPriorities.get(studentId);
		return (priorities == null ? null : priorities.get(courseId));
	}
	
	public class Initialization implements ParallelInitialization.Task {
		private CurriculumClassification iClassification;
		private boolean iUpdateClassification = false;
		private Hashtable<CourseOffering, Set<WeightedStudentId>> iLastLikeStudents;
		private CurModel iModel;
		private Hashtable<Long, CourseOffering> iCourses;
		private List<CurStudent> iCurStudents;
		private Hashtable<WeightedStudentId, Set<CourseOffering>> iStudents;
		private List<WeightedStudentId> iMadeUpStudents;
		private Hashtable<Long, WeightedStudentId> iStudentIds;
		private Assignment<CurVariable, CurValue> iAssignment;
		
		public Initialization(CurriculumClassification classification, Hashtable<CourseOffering, Set<WeightedStudentId>> lastLikeStudents) {
			iClassification = classification;
			iLastLikeStudents = lastLikeStudents;
		}
		
		@Override
		public void setup(org.hibernate.Session hibSession) {
			sLog.debug("Processing " + iClassification.getCurriculum().getAbbv() + " " + iClassification.getName() + " ... (" + iClassification.getNrStudents() + " students, " + iClassification.getCourses().size() + " iCourses)");
			
			iStudents = new Hashtable<WeightedStudentId, Set<CourseOffering>>();
			if (iLastLikeStudents != null) {
				for (Map.Entry<CourseOffering, Set<WeightedStudentId>> entry: iLastLikeStudents.entrySet()) {
					for (WeightedStudentId student: entry.getValue()) {
						Set<CourseOffering> courses = iStudents.get(student);
						if (courses == null) {
							courses = new HashSet<CourseOffering>();
							iStudents.put(student, courses);
						}
						courses.add(entry.getKey());
					}
				}
			}
			
			float totalWeight = 0;
			for (WeightedStudentId student: iStudents.keySet())
				totalWeight += student.getWeight();
			
			sLog.debug("  last-like students: " + totalWeight + ", target: " + iClassification.getNrStudents());
			iMadeUpStudents = new ArrayList<StudentCourseDemands.WeightedStudentId>();
			if (2 * totalWeight < iClassification.getNrStudents()) { // students are less than 1/2 of the requested size -> make up some students
				int studentsToMakeUp = Math.round(iClassification.getNrStudents() - totalWeight);
				sLog.debug("    making up " + studentsToMakeUp + " students");
				String majors = "";
				for (PosMajor major: iClassification.getCurriculum().getMajors()) {
					if (!majors.isEmpty()) majors += "|";
					majors += major.getCode();
				}
				for (int i = 0; i < studentsToMakeUp; i++) {
					WeightedStudentId student = new WeightedStudentId(-iLastStudentId.newId());
					student.setStats(iClassification.getCurriculum().getAcademicArea().getAcademicAreaAbbreviation(), iClassification.getAcademicClassification().getCode(), majors);
					iStudents.put(student, new HashSet<CourseOffering>());
					iMadeUpStudents.add(student);
				}
			} else { // change weights to fit the requested size
				float factor = iClassification.getNrStudents() / totalWeight;
				sLog.debug("    changing student weight " + factor + " times");
				for (WeightedStudentId student: iStudents.keySet())
					student.setWeight(student.getWeight() * factor);
			}
			
			// Setup model
			iCurStudents = new ArrayList<CurStudent>();
			iStudentIds = new Hashtable<Long, WeightedStudentId>();
			int idx = 0;
			for (WeightedStudentId student: iStudents.keySet()) {
				iCurStudents.add(new CurStudent(student.getStudentId() < 0 ? - (++idx) : student.getStudentId(), student.getWeight()));
				iStudentIds.put(student.getStudentId() < 0 ? - idx : student.getStudentId(), student);
			}
			iModel = new CurModel(iCurStudents);
			iCourses = new Hashtable<Long, CourseOffering>();
			for (CurriculumCourse course: iClassification.getCourses()) {
				iModel.addCourse(course.getCourse().getUniqueId(), course.getCourse().getCourseName(), iClassification.getNrStudents() * course.getPercShare(), iEnrollmentPriorityProvider.getEnrollmentPriority(course));
				iCourses.put(course.getCourse().getUniqueId(), course.getCourse());
				Hashtable<String,Set<String>> curricula = iLoadedCurricula.get(course.getCourse().getUniqueId());
				if (curricula == null) {
					curricula = new Hashtable<String, Set<String>>();
					iLoadedCurricula.put(course.getCourse().getUniqueId(), curricula);
				}
				Set<String> majors = curricula.get(iClassification.getCurriculum().getAcademicArea().getAcademicAreaAbbreviation());
				if (majors == null) {
					majors = new HashSet<String>();
					curricula.put(iClassification.getCurriculum().getAcademicArea().getAcademicAreaAbbreviation(), majors);
				}
				if (iClassification.getCurriculum().getMajors().isEmpty()) {
					majors.add("");
				} else {
					for (PosMajor mj: iClassification.getCurriculum().getMajors())
						majors.add(mj.getCode());
				}
			}
			computeTargetShare(iClassification, iModel);
			if (iSetStudentCourseLimits)
				iModel.setStudentLimits();
			
			// Load model from cache (if exists)
			Solution<CurVariable, CurValue> cachedSolution = null;
			iAssignment = new DefaultSingleAssignment<CurVariable, CurValue>();
			Element cache = (iClassification.getStudents() == null ? null : iClassification.getStudents().getRootElement());
			if (cache != null && cache.getName().equals(getCacheName())) {
				cachedSolution = CurModel.loadFromXml(cache);
				if (iSetStudentCourseLimits)
					((CurModel)cachedSolution.getModel()).setStudentLimits();
			}

			// Check the cached model
			if (cachedSolution != null && ((CurModel)cachedSolution.getModel()).isSameModel(iModel)) {
				// Reuse
				sLog.debug("  using cached model...");
				iModel = ((CurModel)cachedSolution.getModel());
				iAssignment = cachedSolution.getAssignment();
			} else {
				// initial iAssignment
				for (CurStudent student: iCurStudents) { 
					for (CourseOffering course: iStudents.get(iStudentIds.get(student.getStudentId()))) {
						CurCourse curCourse = iModel.getCourse(course.getUniqueId());
						if (curCourse == null) continue;
						CurVariable var = null;
						for (CurVariable v: curCourse.variables())
							if (iAssignment.getValue(v) == null) { var = v; break; }
						if (var != null) {
							CurValue val = new CurValue(var, student);
							if (!iModel.inConflict(iAssignment, val))
								iAssignment.assign(0, val);
							else {
								sLog.debug("Unable to assign " + student + " to " + var);
								Map<Constraint<CurVariable, CurValue>, Set<CurValue>> conf = iModel.conflictConstraints(iAssignment, val);
								for (Map.Entry<Constraint<CurVariable, CurValue>, Set<CurValue>> entry: conf.entrySet()) {
									sLog.debug(entry.getKey() + ": " + entry.getValue());
								}
							}
						} else {
							sLog.debug("No variable for " + student + " to " + curCourse);
						}
					}
				}
				
				iUpdateClassification = true;
			}
		}
		
		public void execute() {
			if (iUpdateClassification) {
				// Solve model
				sLog.debug("Initial: " + iModel.getInfo(iAssignment));
				iModel.solve(iProperties, iAssignment);
				sLog.debug("Final: " + iModel.getInfo(iAssignment));
			}

		}
		
		@Override
		public void teardown(Session hibSession) {
			if (iUpdateClassification) {
				// Save into the cache
				Document doc = DocumentHelper.createDocument();
				iModel.saveAsXml(doc.addElement(getCacheName()), iAssignment);
				// sLog.debug("Model:\n" + doc.asXML());
				iClassification.setStudents(doc);

				hibSession.update(iClassification);
			}
			// Save results
			int idx = 0;
			for (CurStudent s: iModel.getStudents()) {
				WeightedStudentId student = null;
				if (s.getStudentId() < 0) {
					student = iMadeUpStudents.get(idx++);
				} else {
					student = iStudentIds.get(s.getStudentId());
				}
				Set<WeightedCourseOffering> studentCourses = new HashSet<WeightedCourseOffering>();
				iStudentRequests.put(student.getStudentId(), studentCourses);
				Hashtable<Long, Double> priorities = new Hashtable<Long, Double>(); iEnrollmentPriorities.put(student.getStudentId(), priorities);
				for (CurCourse course: s.getCourses(iAssignment)) {
					CourseOffering co = iCourses.get(course.getCourseId());
					if (course.getPriority() != null) priorities.put(co.getUniqueId(), course.getPriority());
					Set<WeightedStudentId> courseStudents = iDemands.get(co.getUniqueId());
					if (courseStudents == null) {
						courseStudents = new HashSet<WeightedStudentId>();
						iDemands.put(co.getUniqueId(), courseStudents);
					}
					courseStudents.add(student);
					studentCourses.add(new WeightedCourseOffering(co, student.getWeight()));
				}
			}
		}
	}
}
