

-- 구매 기록 테이블
create table item_purchase
(
    id              bigint auto_increment
        primary key,
    created_at      datetime(6)                                               null,
    updated_at      datetime(6)                                               null,
    product_type    enum ('SEASON', 'THREE_TO_THREE', 'TICKET', 'TWO_TO_TWO') not null,
    total_price     bigint                                                    not null,
    vat             bigint                                                    null,
    user_id         bigint                                                    null,
    team_id         bigint                                                    null,
    user_profile_id bigint                                                    null,
    constraint FK34q2m8qjkq74ib1r0w6lp433j
        foreign key (user_profile_id) references user_profile (user_profile_id),
    constraint FK4j64njc19ymbulm5pyn1edcqa
        foreign key (team_id) references team (team_id),
    constraint FK8l8k7ig0e16d0u37n4ed3rij4
        foreign key (user_id) references user (user_id)
);

-- kakao_pay
create table ka_kao_pay_data
(
    id           bigint auto_increment
        primary key,
    created_at   datetime(6)                                               null,
    updated_at   datetime(6)                                               null,
    order_id     varchar(255)                                              null,
    product_type enum ('SEASON', 'THREE_TO_THREE', 'TICKET', 'TWO_TO_TWO') not null,
    tid          varchar(255)                                              null,
    total_price  bigint                                                    not null,
    user_id      bigint                                                    null,
    item_id      bigint                                                    null,
    constraint FK6mtq83fqbgng2lgv46xd7l79c
        foreign key (user_id) references user (user_id),
    constraint FKgim4bim7btfchwlv3sum53q7c
        foreign key (item_id) references item_purchase (id)
);


