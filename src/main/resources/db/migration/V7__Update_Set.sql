-- matching 테이블 생성
create table matching
(
    matching_id     bigint auto_increment
        primary key,
    created_at      datetime(6)                  null,
    updated_at      datetime(6)                  null,
    matching_status enum ('COMPLETE', 'WAITING') not null
);

-- user_matching 테이블 생성
create table user_matching
(
    user_matching_id bigint auto_increment
        primary key,
    created_at       datetime(6) null,
    updated_at       datetime(6) null,
    matching_id      bigint      null,
    user_id          bigint      null,
    constraint FKl7gejlkdtjoe3p2n18i9rv9pk
        foreign key (user_id) references user (user_id),
    constraint FKmvmym7ftbi2j5eabgul9xuiqc
        foreign key (matching_id) references matching (matching_id)
);
