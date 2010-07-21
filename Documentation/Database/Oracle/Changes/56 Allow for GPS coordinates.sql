/*
 * UniTime 3.2 (University Timetabling Application)
 * Copyright (C) 2010, UniTime LLC
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

alter table building modify coordinate_x double precision;
alter table building modify coordinate_y double precision;
alter table room modify coordinate_x double precision;
alter table room modify coordinate_y double precision;
alter table non_university_location modify coordinate_x double precision;
alter table non_university_location modify coordinate_y double precision;
alter table external_building modify coordinate_x double precision;
alter table external_building modify coordinate_y double precision;
alter table external_room modify coordinate_x double precision;
alter table external_room modify coordinate_y double precision;

alter table building drop constraint nn_building_coordinate_x;
alter table building drop constraint nn_building_coordinate_y;
alter table room drop constraint nn_room_coordinate_x;
alter table room drop constraint nn_room_coordinate_y;
alter table non_university_location drop constraint nn_non_univ_loc_coord_x;
alter table non_university_location drop constraint nn_non_univ_loc_coord_y;

update building set coordinate_x = null where coordinate_x = -1;
update building set coordinate_y = null where coordinate_y = -1;
update room set coordinate_x = null where coordinate_x = -1;
update room set coordinate_y = null where coordinate_y = -1;
update non_university_location set coordinate_x = null where coordinate_x = -1;
update non_university_location set coordinate_y = null where coordinate_y = -1;
update external_building set coordinate_x = null where coordinate_x = -1;
update external_building set coordinate_y = null where coordinate_y = -1;
update external_room set coordinate_x = null where coordinate_x = -1;
update external_room set coordinate_y = null where coordinate_y = -1;
		
/**
 * Update database version
 */

update application_config set value='56' where name='tmtbl.db.version';

commit;
