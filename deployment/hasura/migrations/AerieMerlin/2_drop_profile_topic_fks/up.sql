alter table profile_segment drop constraint profile_segment_owned_by_profile;
alter table event drop constraint event_owned_by_topic;

alter table span drop constraint span_has_parent_span;
alter table span drop constraint span_owned_by_dataset;

comment on table profile is e''
  'Metadata describing a resource profile in the context of a dataset.';
comment on column profile.type is e''
  'The type of behavior this profile expresses. Any segments associated with this profile should abide by this type.';

create or replace function delete_profile_cascade()
  returns trigger
  security invoker
  language plpgsql as $$begin
  delete from profile_segment
  where profile_segment.dataset_id = old.dataset_id and profile_segment.profile_id = old.id;
  return old;
end$$;

create or replace function delete_dataset_cascade()
  returns trigger
  security definer
  language plpgsql as $$begin
  delete from span where span.dataset_id = old.id;
  return old;
end$$;

create trigger delete_profile_trigger
  after delete on profile
  for each row
execute function delete_profile_cascade();

create or replace function update_profile_cascade()
  returns trigger
  security invoker
  language plpgsql as $$begin
  if old.id != new.id or old.dataset_id != new.dataset_id
  then
    update profile_segment
    set profile_id = new.id,
        dataset_id = new.dataset_id
    where profile_segment.dataset_id = old.dataset_id and profile_segment.profile_id = old.id;
  end if;
  return new;
end$$;

create trigger update_profile_trigger
  after update on profile
  for each row
execute function update_profile_cascade();

create or replace function delete_topic_cascade()
  returns trigger
  security invoker
  language plpgsql as $$begin
  delete from event
  where event.topic_index = old.topic_index and event.dataset_id = old.dataset_id;
  return old;
end$$;

create trigger delete_topic_trigger
  after delete on topic
  for each row
execute function delete_topic_cascade();

create or replace function update_topic_cascade()
  returns trigger
  security invoker
  language plpgsql as $$begin
  if old.topic_index != new.topic_index or old.dataset_id != new.dataset_id
  then
    update event
    set topic_index = new.topic_index,
        dataset_id = new.dataset_id
    where event.dataset_id = old.dataset_id and event.topic_index = old.topic_index;
  end if;
  return new;
end$$;

create trigger update_topic_trigger
  after update on topic
  for each row
execute function update_topic_cascade();

create or replace function profile_segment_integrity_function()
  returns trigger
  security invoker
  language plpgsql as $$begin
  if not exists(
    select from profile
      where profile.dataset_id = new.dataset_id
      and profile.id = new.profile_id
    for key share of profile)
  then
    raise exception 'foreign key violation: there is no profile with id % in dataset %', new.profile_id, new.dataset_id;
  end if;
  return new;
end$$;

create constraint trigger insert_update_profile_segment_trigger
  after insert or update on profile_segment
  for each row
execute function profile_segment_integrity_function();

create or replace function event_integrity_function()
  returns trigger
  security invoker
  language plpgsql as $$begin
  if not exists(
    select from topic
      where topic.dataset_id = new.dataset_id
      and topic.topic_index = new.topic_index
    for key share of topic)
  then
    raise exception 'foreign key violation: there is no topic with topic_index % in dataset %', new.topic_index, new.dataset_id;
  end if;
  return new;
end$$;

create constraint trigger insert_update_event_trigger
  after insert or update on event
  for each row
execute function event_integrity_function();

create or replace function span_integrity_function()
  returns trigger
  security invoker
  language plpgsql as $$begin
  if not exists(select from dataset where dataset.id = new.dataset_id for key share of dataset)
  then
    raise exception 'foreign key violation: there is no dataset with id %', new.dataset_id;
  end if;
  return new;
end$$;

create constraint trigger insert_update_span_trigger
  after insert or update on span
  for each row
execute function span_integrity_function();

do $$
  declare
    dataset_id integer;
  begin
    for dataset_id in select id from dataset
      loop
        if exists(select from pg_tables where schemaname = 'public' and tablename = 'span_' || dataset_id) then
          execute 'alter table span_' || dataset_id || ' add constraint span_has_parent_span foreign key (dataset_id, parent_id) references span_' || dataset_id || ' on update cascade on delete cascade;';
        end if;
      end loop;
  end
$$;

create or replace function delete_dataset_cascade()
  returns trigger
  security definer
  language plpgsql as $$begin
  delete from span where span.dataset_id = old.id;
  return old;
end$$;

create trigger delete_dataset_trigger
  after delete on dataset
  for each row
execute function delete_dataset_cascade();

call migrations.mark_migration_applied('2');
