create table transcripts (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  text text not null,
  language text not null,
  created_at bigint not null,
  duration_ms bigint not null,
  title text,
  summary text,
  analyzed_at bigint,
  updated_at bigint not null,
  deleted_at bigint
);
create index on transcripts (user_id, updated_at);
alter table transcripts enable row level security;
create policy "own rows" on transcripts for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table todo_items (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  source_transcript_id uuid not null,
  text text not null,
  due_at bigint not null,
  has_time boolean not null,
  notify boolean not null default true,
  done boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);
create index on todo_items (user_id, updated_at);
alter table todo_items enable row level security;
create policy "own rows" on todo_items for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
