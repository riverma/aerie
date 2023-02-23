drop procedure span_add_foreign_key_to_partition;

drop function allocate_dataset_partitions;

drop trigger if exists delete_dataset_trigger on dataset;
drop function if exists delete_dataset_cascade;

drop trigger if exists delete_profile_trigger on profile;
drop function if exists delete_profile_cascade;

drop trigger if exists delete_topic_trigger on topic;
drop function if exists delete_topic_cascade;

drop trigger if exists update_profile_trigger on profile;
drop function if exists update_profile_cascade;

drop trigger if exists update_topic_trigger on topic;
drop function if exists update_topic_cascade;

do $$
declare
  dataset_id integer;
begin
  for dataset_id in select id from dataset
  loop
    if exists(select from pg_tables where schemaname = 'public' and tablename = 'span_' || dataset_id) then
      execute 'alter table span_' || dataset_id || ' drop constraint span_has_parent_span;';
    end if;
  end loop;
end
$$;

drop trigger if exists insert_update_profile_segment_trigger on profile_segment;
drop function if exists profile_segment_integrity_function;

drop trigger if exists insert_update_event_trigger on event;
drop function if exists event_integrity_function;

drop trigger if exists insert_update_span_trigger on span;
drop function if exists span_integrity_function;

alter table profile_segment
  add constraint profile_segment_owned_by_profile
    foreign key (profile_id)
    references profile
    on update cascade
    on delete cascade;

alter table event
  add constraint event_owned_by_topic
    foreign key (dataset_id, topic_index)
    references topic
    on update cascade
    on delete cascade;

alter table span
  add constraint span_owned_by_dataset
    foreign key (dataset_id)
      references dataset
      on update cascade
      on delete cascade;
alter table span
  add constraint span_has_parent_span
    foreign key (dataset_id, parent_id)
    references span
    on update cascade
       on delete cascade;

comment on table profile is e''
  'The behavior of a resource over time, in the context of a dataset.';

comment on column profile.type is e''
  'The type of behavior this profile expresses. The segments of this profile must abide by this type.';

call migrations.mark_migration_rolled_back('2');
