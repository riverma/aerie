create table profile (
  id integer generated always as identity,
  dataset_id integer not null,

  name text not null,
  type jsonb null,
  duration interval not null,

  constraint profile_synthetic_key
    primary key (id),
  constraint profile_natural_key
    unique (dataset_id, name),
  constraint profile_owned_by_dataset
    foreign key (dataset_id)
    references dataset
    on update cascade
    on delete cascade
);

comment on table profile is e''
  'The behavior of a resource over time, in the context of a dataset.';
comment on column profile.dataset_id is e''
  'The dataset this profile is part of.';
comment on column profile.name is e''
  'A human-readable name for this profile, unique within its containing dataset.';
comment on column profile.type is e''
  'The type of behavior this profile expresses. The segments of this profile must abide by this type.';
comment on column profile.duration is e''
  'The duration of the profile after the start time stored in the dataset.';

create or replace function delete_profile_cascade()
  returns trigger
  security invoker
  language plpgsql as $$begin
    delete from profile_segment
    where profile_segment.dataset_id = old.dataset_id and profile_segment.profile_id = old.id;
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
