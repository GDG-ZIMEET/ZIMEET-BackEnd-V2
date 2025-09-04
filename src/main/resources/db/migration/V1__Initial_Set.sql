create table chat_room
(
    chat_room_id bigint auto_increment
        primary key,
    created_at   datetime(6)  null,
    updated_at   datetime(6)  null,
    name         varchar(255) not null
);

create table club
(
    club_id    bigint auto_increment
        primary key,
    created_at datetime(6)                               null,
    updated_at datetime(6)                               null,
    account    varchar(255)                              null,
    category   enum ('DATE', 'EVENT', 'FOOD', 'GOODS')   not null,
    info       varchar(255)                              not null,
    name       varchar(255)                              not null,
    place      enum ('A', 'K', 'N', 'S_LEFT', 'S_RIGHT') not null,
    rep        varchar(255)                              not null,
    time       varchar(255)                              not null
);

create table item
(
    item_id    bigint auto_increment
        primary key,
    created_at datetime(6)  null,
    updated_at datetime(6)  null,
    content    varchar(255) not null,
    name       varchar(255) not null,
    club_id    bigint       null,
    constraint FK7jci41cytknpgdb9orcak68tn
        foreign key (club_id) references club (club_id)
);

create table notification
(
    notification_id bigint auto_increment
        primary key,
    created_at      datetime(6) null,
    updated_at      datetime(6) null
);

create table refresh_token
(
    refresh_token_id bigint auto_increment
        primary key,
    key_id           varchar(255) null,
    refresh_token    varchar(255) null
);

create table team
(
    team_id    bigint auto_increment
        primary key,
    created_at datetime(6)                           null,
    updated_at datetime(6)                           null,
    gender     enum ('FEMALE', 'MALE')               not null,
    hi         int                                   not null,
    name       varchar(7)                            not null,
    team_type  enum ('THREE_TO_THREE', 'TWO_TO_TWO') not null
);

create table hi
(
    hi_id      bigint auto_increment
        primary key,
    created_at datetime(6) null,
    updated_at datetime(6) null,
    from_id    bigint      null,
    to_id      bigint      null,
    constraint FK93t6nv2nrncgegqvf8nof28mh
        foreign key (to_id) references team (team_id),
    constraint FKg8a8236bmo2tpk3fmdang296k
        foreign key (from_id) references team (team_id)
);

create table terms
(
    terms_id   bigint auto_increment
        primary key,
    created_at datetime(6)  null,
    updated_at datetime(6)  null,
    content    varchar(255) not null,
    optional   bit          not null,
    title      varchar(255) not null
);

create table user
(
    user_id        bigint auto_increment
        primary key,
    name           varchar(255) not null,
    password       varchar(255) not null,
    student_number varchar(255) not null,
    constraint UKgj2fy3dcix7ph7k8684gka40c
        unique (name),
    constraint UKkiqfjabx9puw3p1eg7kily8kg
        unique (password),
    constraint UKqul7p1hvudixi2dmpg80b66h7
        unique (student_number)
);

create table join_chat
(
    join_chat_id bigint auto_increment
        primary key,
    created_at   datetime(6) null,
    updated_at   datetime(6) null,
    chat_room_id bigint      null,
    user_id      bigint      null,
    constraint FK62dyjbyaa4b5fkvf2newgn1b9
        foreign key (user_id) references user (user_id),
    constraint FKb2gkn8xo300ca9dmavoffmgam
        foreign key (chat_room_id) references chat_room (chat_room_id)
);

create table message
(
    message_id   bigint auto_increment
        primary key,
    created_at   datetime(6)  null,
    updated_at   datetime(6)  null,
    content      varchar(255) not null,
    is_read      bit          null,
    chat_room_id bigint       null,
    user_id      bigint       null,
    constraint FK5i8ac68n051032d9ga7gg6i85
        foreign key (chat_room_id) references chat_room (chat_room_id),
    constraint FKb3y6etti1cfougkdr0qiiemgv
        foreign key (user_id) references user (user_id)
);

create table orders
(
    order_id         varchar(255) not null
        primary key,
    created_at       datetime(6)  null,
    updated_at       datetime(6)  null,
    toss_payments_id varchar(255) null,
    user_id          bigint       null,
    constraint UK4logqexg51buromq1npga66so
        unique (toss_payments_id),
    constraint FKel9kyl84ego2otj2accfd8mr7
        foreign key (user_id) references user (user_id)
);

create table toss_payments
(
    toss_payments_id    varchar(255)                                                                                                       not null
        primary key,
    created_at          datetime(6)                                                                                                        null,
    updated_at          datetime(6)                                                                                                        null,
    approved_at         datetime(6)                                                                                                        null,
    requested_at        datetime(6)                                                                                                        not null,
    toss_order_id       varchar(255)                                                                                                       not null,
    toss_payment_key    varchar(255)                                                                                                       not null,
    toss_payment_method enum ('CARD', 'EASY_PAYMENT')                                                                                      not null,
    toss_payment_status enum ('ABORTED', 'CANCELED', 'DONE', 'EXPIRED', 'IN_PROGRESS', 'PARTIAL_CANCELED', 'READY', 'WAITING_FOR_DEPOSIT') not null,
    total_amount        bigint                                                                                                             not null,
    orders_id           varchar(255)                                                                                                       not null,
    constraint UK45lmwc8jxm3taxnv6hhnulk6a
        unique (toss_payment_key),
    constraint UKsp79frhmfnbkrbleuwriwtvg1
        unique (orders_id),
    constraint FKnjurpc78kvqix69kx89jsqjow
        foreign key (orders_id) references orders (order_id)
);

alter table orders
    add constraint FKmxrhje0pnraft89bdpvi1dbki
        foreign key (toss_payments_id) references toss_payments (toss_payments_id);

create table user_profile
(
    user_profile_id bigint auto_increment
        primary key,
    age             int                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    not null,
    delete_team     int                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    not null,
    emoji           enum ('GEM', 'HEART_ON_FIRE')                                                                                                                                                                                                                                                                                                                                                                                                                                                                          not null,
    gender          enum ('FEMALE', 'MALE')                                                                                                                                                                                                                                                                                                                                                                                                                                                                                not null,
    grade           enum ('FIRST', 'FOURTH', 'SECOND', 'THIRD')                                                                                                                                                                                                                                                                                                                                                                                                                                                            not null,
    ideal_age       enum ('NO_MATTER', 'OLDER', 'SAME', 'YOUNGER')                                                                                                                                                                                                                                                                                                                                                                                                                                                         not null,
    ideal_type      enum ('BEAR', 'CAT', 'DINOSAUR', 'DOG', 'FOX', 'HAMSTER', 'RABBIT', 'WOLF')                                                                                                                                                                                                                                                                                                                                                                                                                            not null,
    level           tinyint                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                not null,
    major           enum ('ACCOUNTING', 'AI', 'BIOTECH', 'BMCE', 'BMSW', 'BUSINESS', 'CHEMISTRY', 'CHILDREN', 'CHINESE', 'CLOTHING', 'CSIE', 'DA', 'DESIGN', 'ECONOMICS', 'ENGLISH', 'ENVI', 'FOOD_NUTRITION', 'FRENCH', 'GBS', 'ICE', 'INTERNATIONAL_STUDIES', 'JAPANESE', 'KOREAN', 'KOREANHISTORY', 'LAW', 'MATH', 'MEDICAL_BIOLOGY', 'MEDICINE', 'MTC', 'MUSIC', 'NURSING', 'PHARMACY', 'PHILOSOPHY', 'PHYSICS', 'PSYCHOLOGY', 'PUBLIC_ADMINISTRATION', 'SOCIALWELFARE', 'SOCIOLOGY', 'SPECIAL_EDUCATION', 'THEOLOGY') not null,
    mbti            enum ('ENFJ', 'ENFP', 'ENTJ', 'ENTP', 'ESFJ', 'ESFP', 'ESTJ', 'ESTP', 'INFJ', 'INFP', 'INTJ', 'INTP', 'ISFJ', 'ISFP', 'ISTJ', 'ISTP')                                                                                                                                                                                                                                                                                                                                                                  not null,
    music           enum ('BALLOD', 'BAND', 'CLASSICAL', 'HIPHOP', 'JPOP', 'KPOP', 'POP', 'ZAZZ')                                                                                                                                                                                                                                                                                                                                                                                                                          not null,
    nickname        varchar(255)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           not null,
    style           enum ('CASUAL', 'CUTIE', 'FORMAL', 'HEAP', 'NEAT', 'SPORTY', 'STREET', 'VINTAGE')                                                                                                                                                                                                                                                                                                                                                                                                                      not null,
    user_id         bigint                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 not null,
    constraint UKebc21hy5j7scdvcjt0jy6xxrv
        unique (user_id),
    constraint UKm9ga0crhcge7onj1gx9a5lnjy
        unique (nickname),
    constraint FK6kwj5lk78pnhwor4pgosvb51r
        foreign key (user_id) references user (user_id)
);

create table user_team
(
    user_team_id bigint auto_increment
        primary key,
    created_at   datetime(6) null,
    updated_at   datetime(6) null,
    team_id      bigint      null,
    user_id      bigint      null,
    constraint FK6d6agqknw564xtsa91d3259wu
        foreign key (team_id) references team (team_id),
    constraint FKd6um0sk8hyytfq7oalt5a4nph
        foreign key (user_id) references user (user_id)
);

create table user_terms
(
    user_terms_id bigint auto_increment
        primary key,
    created_at    datetime(6) null,
    updated_at    datetime(6) null,
    agree         bit         not null,
    terms_id      bigint      null,
    user_id       bigint      null,
    constraint FKi12icigeb1prcyhv7cdfyyv4e
        foreign key (user_id) references user (user_id),
    constraint FKsv90iyco7g8yl9kbml0m5pjhl
        foreign key (terms_id) references terms (terms_id)
);

