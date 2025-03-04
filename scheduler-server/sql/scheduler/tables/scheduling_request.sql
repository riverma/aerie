create type status_t as enum('pending', 'incomplete', 'failed', 'success');

create table scheduling_request (
  specification_id integer not null,
  analysis_id integer not null,

  status status_t not null default 'pending',
  reason jsonb null,
  canceled boolean not null default false,

  specification_revision integer not null,

  constraint scheduling_request_primary_key
    primary key(specification_id, specification_revision),
  constraint scheduling_request_analysis_unique
    unique (analysis_id),
  constraint scheduling_request_references_scheduling_specification
    foreign key(specification_id)
      references scheduling_specification
      on update cascade
      on delete cascade,
  constraint scheduling_request_references_analysis
    foreign key(analysis_id)
      references scheduling_analysis
      on update cascade
      on delete cascade
);

comment on table scheduling_request is e''
  'The status of a scheduling run that is to be performed (or has been performed).';
comment on column scheduling_request.specification_id is e''
  'The ID of scheduling specification for this scheduling run.';
comment on column scheduling_request.analysis_id is e''
  'The ID associated with the analysis of this scheduling run.';
comment on column scheduling_request.status is e''
  'The state of the the scheduling request.';
comment on column scheduling_request.reason is e''
  'The reason for failure when a scheduling request fails.';
comment on column scheduling_request.specification_revision is e''
  'The revision of the scheduling_specification associated with this request.';

create or replace function create_scheduling_analysis()
returns trigger
security definer
language plpgsql as $$begin
  insert into scheduling_analysis
  default values
  returning id into new.analysis_id;
return new;
end$$;

do $$ begin
create trigger create_scheduling_analysis_trigger
  before insert on scheduling_request
  for each row
  execute function create_scheduling_analysis();
exception
  when duplicate_object then null;
end $$;

-- Scheduling request NOTIFY triggers
-- These triggers NOTIFY LISTEN(ing) scheduler worker clients of pending scheduling requests

create or replace function notify_scheduler_workers ()
returns trigger
security definer
language plpgsql as $$
begin
  perform (
    with payload(specification_revision,
                 specification_id,
                 analysis_id) as
    (
      select NEW.specification_revision,
             NEW.specification_id,
             NEW.analysis_id
    )
    select pg_notify('scheduling_request_notification', json_strip_nulls(row_to_json(payload))::text)
    from payload
  );
  return null;
end$$;

do $$ begin
create trigger notify_scheduler_workers
  after insert on scheduling_request
  for each row
  execute function notify_scheduler_workers();
end $$;
