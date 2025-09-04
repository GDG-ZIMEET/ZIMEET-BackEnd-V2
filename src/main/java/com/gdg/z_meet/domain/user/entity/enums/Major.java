package com.gdg.z_meet.domain.user.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Major {
    // 인문
    // 인문계열
    HUMANITIES("인문계열", "인문계열"),
    KOREAN("국어국문학과", "국문"), PHILOSOPHY("철학과", "철학"), KOREANHISTORY("국사학과", "국사"), CHILDREN("아동학과", "아동"),
    // 어문계열
    LANGUAGE("어문계열", "어문계열"),
    ENGLISH("영어영문학부", "영문"), CHINESE("중국언어문화학과", "중문"), JAPANESE("일어일본문화학과", "일문"), FRENCH("프랑스어문화학과", "프문"),
    // 음악과
    MUSIC("음악과", "음악"),
    // 신학과
    THEOLOGY("신학과", "신학"),
    //사회
    SOCIAL_SCIENCE("사회과학계열", "사과계열"),
    SOCIALWELFARE("사회복지학과", "사복"), PSYCHOLOGY("심리학과", "심리"), SOCIOLOGY("사회학과", "사회"), SPECIAL_EDUCATION("특수교육과", "특교"),
    // 경영계열
    MANAGEMENT("경영계열", "경영계열"),
    BUSINESS("경영학과", "경영"), ACCOUNTING("회계학과", "회계"),
    // 국제, 법정경계열
    INTER_LAW("국제법정경계열", "국법정계열"),
    INTERNATIONAL("국제학부", "국제"), LAW("법학과", "법학"), ECONOMICS("경제학과", "경제"), PUBLIC_ADMINISTRATION("행정학과", "행정"),
    // 글로벌경영대학
    GBS("글로벌경영학부", "글경"),

    // 자연
    // 자연과학계열
    NATURAL_SCIENCE("자연과학계열", "자과계열"),
    CHEMISTRY("화학과", "화학"), MATH("수학과", "수학"), PHYSICS("물리학과", "물리"),
    // 생명과학계열
    LIFE_SCIENCE("생명과학계열", "생과계열"),
    DESIGN("공간디자인소비자학과", "공소"), CLOTHING("의류학과", "의류"), FOOD_NUTRITION("식품영양학과", "식영"),
    // 의생명과학과
    MEDICAL_BIOLOGY("의생명과학과", "의생명"),
    // 약학대학
    PHARMACY("약학과", "약학"),
    // 간호대, 의대
    NURSING("간호학과", "간호"), MEDICINE("의학과", "의대"),

    // 공학
    ICT("ICT공학계열", "ICT계열"),
    CSIE("컴퓨터정보공학부", "컴공"), MTC("미디어기술콘텐츠학과", "미콘"), ICE("정보통신전자공학부", "정통"),
    // 바이오융합공학계열
    BIO("바이오융합공학계열", "바융공계열"),
    BIOTECH("생명공학과", "생명"), ENVI("에너지환경공학과", "에환공"), BMCE("바이오메디컬화학공학과", "바메화공"),
    // 인공지능, 데이터사이언스, 바이오메디컬소프트웨어
    AI("인공지능학과", "인공지능"), DA("데이터사이언스학과", "데사"), BMSW("바이오메디컬소프트웨어학과", "바메솦"),

    // 자유전공
    LIBREAL("자유전공학부", "자전"),

    // 자연공학
    NATURAL_ENGINEERING("자연공학계열", "자연공학"),
    // 인문사회
    HUMAN_SOCIAL("인문사회계열", "인문사회");

    private final String displayName;
    private final String shortName;

    Major(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }
}