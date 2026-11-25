-- PoiWallet: run once in Supabase → SQL Editor.
-- One row per user holds that user's wallet (cards, coupons, deletions) as JSON.

create table if not exists public.wallets (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

alter table public.wallets enable row level security;

-- Each signed-in user can read and write only their own row.
create policy "wallet_select_own" on public.wallets for select using (auth.uid() = user_id);
create policy "wallet_insert_own" on public.wallets for insert with check (auth.uid() = user_id);
create policy "wallet_update_own" on public.wallets for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "wallet_delete_own" on public.wallets for delete using (auth.uid() = user_id);

-- Lets a user delete their own account (required by the App Store, Google Play and GDPR).
create or replace function public.delete_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;

-- ---------- Family sharing (added in 1.5.0) ----------
-- A family shares one wallet. Each person belongs to at most one family.
-- Joining happens only through the security-definer functions below, using the invite code.

create table if not exists public.families (
  id          uuid primary key default gen_random_uuid(),
  owner       uuid references auth.users (id) on delete set null,
  invite_code text not null unique,
  data        jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table if not exists public.family_members (
  user_id   uuid primary key references auth.users (id) on delete cascade,
  family_id uuid not null references public.families (id) on delete cascade,
  joined_at timestamptz not null default now()
);

alter table public.families enable row level security;
alter table public.family_members enable row level security;

create or replace function public.my_family_id()
returns uuid language sql stable security definer set search_path = '' as $$
  select family_id from public.family_members where user_id = auth.uid()
$$;

-- Members read and write their own family's wallet; nobody can list other families.
create policy "family_select_member" on public.families for select using (id = public.my_family_id());
create policy "family_update_member" on public.families for update using (id = public.my_family_id()) with check (id = public.my_family_id());
create policy "members_select_same_family" on public.family_members for select using (family_id = public.my_family_id());

create or replace function public.create_family()
returns text language plpgsql security definer set search_path = '' as $$
declare fid uuid; code text;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if exists (select 1 from public.family_members where user_id = auth.uid()) then raise exception 'already in a family'; end if;
  code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));
  insert into public.families (owner, invite_code) values (auth.uid(), code) returning id into fid;
  insert into public.family_members (user_id, family_id) values (auth.uid(), fid);
  return code;
end; $$;

create or replace function public.join_family(code text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare fid uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  select id into fid from public.families where invite_code = upper(trim(code));
  if fid is null then raise exception 'bad code'; end if;
  if (select count(*) from public.family_members where family_id = fid) >= 6 then raise exception 'family full'; end if;
  insert into public.family_members (user_id, family_id) values (auth.uid(), fid)
    on conflict (user_id) do update set family_id = excluded.family_id, joined_at = now();
  return fid;
end; $$;

-- Leaving keeps a copy of the cards in the person's own wallet (the app does that before calling this).
-- The last member to leave deletes the family.
create or replace function public.leave_family()
returns void language plpgsql security definer set search_path = '' as $$
declare fid uuid;
begin
  select family_id into fid from public.family_members where user_id = auth.uid();
  if fid is null then return; end if;
  delete from public.family_members where user_id = auth.uid();
  if not exists (select 1 from public.family_members where family_id = fid) then
    delete from public.families where id = fid;
  end if;
end; $$;

revoke all on function public.create_family(), public.join_family(text), public.leave_family(), public.my_family_id() from public, anon;
grant execute on function public.create_family(), public.join_family(text), public.leave_family(), public.my_family_id() to authenticated;
