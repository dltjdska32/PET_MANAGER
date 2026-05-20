package com.petmanager.data.repository

import com.petmanager.data.local.dao.RegionDao
import com.petmanager.data.local.entity.RegionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegionRepository @Inject constructor(
    private val regionDao: RegionDao,
) {

    suspend fun getAllSido(): List<RegionEntity> {
        return regionDao.getAllSido()
    }
    
    fun getAllSidoFlow(): Flow<List<RegionEntity>> {
        return regionDao.getAllSidoFlow()
    }
    
    suspend fun getSigunguBySido(parentId: Long): List<RegionEntity> {
        return regionDao.getSigunguBySido(parentId)
    }
    
    fun getSigunguBySidoFlow(parentId: Long): Flow<List<RegionEntity>> {
        return regionDao.getSigunguBySidoFlow(parentId)
    }
    
    suspend fun getRegionById(id: Long): RegionEntity? {
        initializeRegionsIfNeeded()
        return regionDao.getRegionById(id)
    }

    /** 시군구는 "경기 수원시" 형태, 시도는 "서울" 형태로 반환 */
    suspend fun getRegionDisplayName(id: Long): String {
        initializeRegionsIfNeeded()
        val region = regionDao.getRegionById(id) ?: return "지역 $id"
        if (region.level == 1 || region.parentId == null) return region.regionName
        val parent = regionDao.getRegionById(region.parentId)
        return if (parent != null) "${parent.regionName} ${region.regionName}" else region.regionName
    }

    suspend fun getRegionDisplayNames(ids: List<Long>): Map<Long, String> {
        initializeRegionsIfNeeded()
        if (ids.isEmpty()) return emptyMap()
        val regions = regionDao.getRegionsByIds(ids).associateBy { it.id }
        val parentIds = regions.values.mapNotNull { it.parentId }.distinct()
        val parents = if (parentIds.isEmpty()) {
            emptyMap()
        } else {
            regionDao.getRegionsByIds(parentIds).associateBy { it.id }
        }
        return ids.associateWith { id ->
            val region = regions[id]
            if (region == null) "지역 $id" else formatRegionDisplayName(region, parents)
        }
    }

    private fun formatRegionDisplayName(
        region: RegionEntity,
        parentById: Map<Long, RegionEntity> = emptyMap(),
    ): String {
        if (region.level == 1 || region.parentId == null) return region.regionName
        val parentName = parentById[region.parentId]?.regionName ?: return region.regionName
        return "$parentName ${region.regionName}"
    }

    /**
     * 여러 지역 id 를 한 번의 쿼리로 조회. 입력 순서를 보존해서 반환한다.
     * (Room 의 `IN (:ids)` 는 순서를 보장하지 않기 때문에 메모리에서 재정렬)
     */
    suspend fun getRegionsByIds(ids: List<Long>): List<RegionEntity> {
        initializeRegionsIfNeeded()
        if (ids.isEmpty()) return emptyList()
        val fetched = regionDao.getRegionsByIds(ids).associateBy { it.id }
        return ids.mapNotNull { fetched[it] }
    }
    
    suspend fun initializeRegionsIfNeeded() {
        val count = regionDao.getRegionCount()
        if (count == 0) {
            regionDao.insertRegions(createAllRegions())
            return
        }

        // 기존 데이터가 예전 시/도 명칭(특별시/광역시/특별자치도 포함)일 경우 새 데이터로 교체
        val first = regionDao.getAllSido().firstOrNull()
        val needsRefresh = first?.regionName?.contains("특별") == true || first?.regionName?.contains("광역") == true
        if (needsRefresh) {
            regionDao.deleteAllRegions()
            regionDao.insertRegions(createAllRegions())
        }
    }
    
    private fun createAllRegions(): List<RegionEntity> {
        return listOf(
            // Level 1: 시도 (17개)
            RegionEntity(1L, 1, null, "서울"),
            RegionEntity(2L, 1, null, "부산"),
            RegionEntity(3L, 1, null, "대구"),
            RegionEntity(4L, 1, null, "인천"),
            RegionEntity(5L, 1, null, "광주"),
            RegionEntity(6L, 1, null, "대전"),
            RegionEntity(7L, 1, null, "울산"),
            RegionEntity(8L, 1, null, "세종"),
            RegionEntity(9L, 1, null, "경기"),
            RegionEntity(10L, 1, null, "강원"),
            RegionEntity(11L, 1, null, "충북"),
            RegionEntity(12L, 1, null, "충남"),
            RegionEntity(13L, 1, null, "전북"),
            RegionEntity(14L, 1, null, "전남"),
            RegionEntity(15L, 1, null, "경북"),
            RegionEntity(16L, 1, null, "경남"),
            RegionEntity(17L, 1, null, "제주"),
            
            // Level 2: 서울특별시 (25개)
            RegionEntity(18L, 2, 1L, "종로구"),
            RegionEntity(19L, 2, 1L, "중구"),
            RegionEntity(20L, 2, 1L, "용산구"),
            RegionEntity(21L, 2, 1L, "성동구"),
            RegionEntity(22L, 2, 1L, "광진구"),
            RegionEntity(23L, 2, 1L, "동대문구"),
            RegionEntity(24L, 2, 1L, "중랑구"),
            RegionEntity(25L, 2, 1L, "성북구"),
            RegionEntity(26L, 2, 1L, "강북구"),
            RegionEntity(27L, 2, 1L, "도봉구"),
            RegionEntity(28L, 2, 1L, "노원구"),
            RegionEntity(29L, 2, 1L, "은평구"),
            RegionEntity(30L, 2, 1L, "서대문구"),
            RegionEntity(31L, 2, 1L, "마포구"),
            RegionEntity(32L, 2, 1L, "양천구"),
            RegionEntity(33L, 2, 1L, "강서구"),
            RegionEntity(34L, 2, 1L, "구로구"),
            RegionEntity(35L, 2, 1L, "금천구"),
            RegionEntity(36L, 2, 1L, "영등포구"),
            RegionEntity(37L, 2, 1L, "동작구"),
            RegionEntity(38L, 2, 1L, "관악구"),
            RegionEntity(39L, 2, 1L, "서초구"),
            RegionEntity(40L, 2, 1L, "강남구"),
            RegionEntity(41L, 2, 1L, "송파구"),
            RegionEntity(42L, 2, 1L, "강동구"),
            
            // Level 2: 부산광역시 (16개)
            RegionEntity(43L, 2, 2L, "중구"),
            RegionEntity(44L, 2, 2L, "서구"),
            RegionEntity(45L, 2, 2L, "동구"),
            RegionEntity(46L, 2, 2L, "영도구"),
            RegionEntity(47L, 2, 2L, "부산진구"),
            RegionEntity(48L, 2, 2L, "동래구"),
            RegionEntity(49L, 2, 2L, "남구"),
            RegionEntity(50L, 2, 2L, "북구"),
            RegionEntity(51L, 2, 2L, "해운대구"),
            RegionEntity(52L, 2, 2L, "사하구"),
            RegionEntity(53L, 2, 2L, "금정구"),
            RegionEntity(54L, 2, 2L, "강서구"),
            RegionEntity(55L, 2, 2L, "연제구"),
            RegionEntity(56L, 2, 2L, "수영구"),
            RegionEntity(57L, 2, 2L, "사상구"),
            RegionEntity(58L, 2, 2L, "기장군"),
            
            // Level 2: 대구광역시 (8개)
            RegionEntity(59L, 2, 3L, "중구"),
            RegionEntity(60L, 2, 3L, "동구"),
            RegionEntity(61L, 2, 3L, "서구"),
            RegionEntity(62L, 2, 3L, "남구"),
            RegionEntity(63L, 2, 3L, "북구"),
            RegionEntity(64L, 2, 3L, "수성구"),
            RegionEntity(65L, 2, 3L, "달서구"),
            RegionEntity(66L, 2, 3L, "달성군"),
            
            // Level 2: 인천광역시 (10개)
            RegionEntity(67L, 2, 4L, "중구"),
            RegionEntity(68L, 2, 4L, "동구"),
            RegionEntity(69L, 2, 4L, "미추홀구"),
            RegionEntity(70L, 2, 4L, "연수구"),
            RegionEntity(71L, 2, 4L, "남동구"),
            RegionEntity(72L, 2, 4L, "부평구"),
            RegionEntity(73L, 2, 4L, "계양구"),
            RegionEntity(74L, 2, 4L, "서구"),
            RegionEntity(75L, 2, 4L, "강화군"),
            RegionEntity(76L, 2, 4L, "옹진군"),
            
            // Level 2: 광주광역시 (5개)
            RegionEntity(77L, 2, 5L, "동구"),
            RegionEntity(78L, 2, 5L, "서구"),
            RegionEntity(79L, 2, 5L, "남구"),
            RegionEntity(80L, 2, 5L, "북구"),
            RegionEntity(81L, 2, 5L, "광산구"),
            
            // Level 2: 대전광역시 (5개)
            RegionEntity(82L, 2, 6L, "동구"),
            RegionEntity(83L, 2, 6L, "중구"),
            RegionEntity(84L, 2, 6L, "서구"),
            RegionEntity(85L, 2, 6L, "유성구"),
            RegionEntity(86L, 2, 6L, "대덕구"),
            
            // Level 2: 울산광역시 (5개)
            RegionEntity(87L, 2, 7L, "중구"),
            RegionEntity(88L, 2, 7L, "남구"),
            RegionEntity(89L, 2, 7L, "동구"),
            RegionEntity(90L, 2, 7L, "북구"),
            RegionEntity(91L, 2, 7L, "울주군"),
            
            // Level 2: 세종특별자치시 (1개)
            RegionEntity(92L, 2, 8L, "세종시"),
            
            // Level 2: 경기도 (31개)
            RegionEntity(93L, 2, 9L, "수원시"),
            RegionEntity(94L, 2, 9L, "성남시"),
            RegionEntity(95L, 2, 9L, "의정부시"),
            RegionEntity(96L, 2, 9L, "안양시"),
            RegionEntity(97L, 2, 9L, "부천시"),
            RegionEntity(98L, 2, 9L, "광명시"),
            RegionEntity(99L, 2, 9L, "평택시"),
            RegionEntity(100L, 2, 9L, "동두천시"),
            RegionEntity(101L, 2, 9L, "안산시"),
            RegionEntity(102L, 2, 9L, "고양시"),
            RegionEntity(103L, 2, 9L, "과천시"),
            RegionEntity(104L, 2, 9L, "구리시"),
            RegionEntity(105L, 2, 9L, "남양주시"),
            RegionEntity(106L, 2, 9L, "오산시"),
            RegionEntity(107L, 2, 9L, "시흥시"),
            RegionEntity(108L, 2, 9L, "군포시"),
            RegionEntity(109L, 2, 9L, "의왕시"),
            RegionEntity(110L, 2, 9L, "하남시"),
            RegionEntity(111L, 2, 9L, "용인시"),
            RegionEntity(112L, 2, 9L, "파주시"),
            RegionEntity(113L, 2, 9L, "이천시"),
            RegionEntity(114L, 2, 9L, "안성시"),
            RegionEntity(115L, 2, 9L, "김포시"),
            RegionEntity(116L, 2, 9L, "화성시"),
            RegionEntity(117L, 2, 9L, "광주시"),
            RegionEntity(118L, 2, 9L, "양주시"),
            RegionEntity(119L, 2, 9L, "포천시"),
            RegionEntity(120L, 2, 9L, "여주시"),
            RegionEntity(121L, 2, 9L, "양평군"),
            RegionEntity(122L, 2, 9L, "가평군"),
            RegionEntity(123L, 2, 9L, "연천군"),
            
            // Level 2: 강원특별자치도 (18개)
            RegionEntity(124L, 2, 10L, "춘천시"),
            RegionEntity(125L, 2, 10L, "원주시"),
            RegionEntity(126L, 2, 10L, "강릉시"),
            RegionEntity(127L, 2, 10L, "동해시"),
            RegionEntity(128L, 2, 10L, "태백시"),
            RegionEntity(129L, 2, 10L, "속초시"),
            RegionEntity(130L, 2, 10L, "삼척시"),
            RegionEntity(131L, 2, 10L, "홍천군"),
            RegionEntity(132L, 2, 10L, "횡성군"),
            RegionEntity(133L, 2, 10L, "영월군"),
            RegionEntity(134L, 2, 10L, "평창군"),
            RegionEntity(135L, 2, 10L, "정선군"),
            RegionEntity(136L, 2, 10L, "철원군"),
            RegionEntity(137L, 2, 10L, "화천군"),
            RegionEntity(138L, 2, 10L, "양구군"),
            RegionEntity(139L, 2, 10L, "인제군"),
            RegionEntity(140L, 2, 10L, "고성군"),
            RegionEntity(141L, 2, 10L, "양양군"),
            
            // Level 2: 충청북도 (11개)
            RegionEntity(142L, 2, 11L, "청주시"),
            RegionEntity(143L, 2, 11L, "충주시"),
            RegionEntity(144L, 2, 11L, "제천시"),
            RegionEntity(145L, 2, 11L, "보은군"),
            RegionEntity(146L, 2, 11L, "옥천군"),
            RegionEntity(147L, 2, 11L, "영동군"),
            RegionEntity(148L, 2, 11L, "진천군"),
            RegionEntity(149L, 2, 11L, "괴산군"),
            RegionEntity(150L, 2, 11L, "음성군"),
            RegionEntity(151L, 2, 11L, "단양군"),
            RegionEntity(152L, 2, 11L, "증평군"),
            
            // Level 2: 충청남도 (15개)
            RegionEntity(153L, 2, 12L, "천안시"),
            RegionEntity(154L, 2, 12L, "공주시"),
            RegionEntity(155L, 2, 12L, "보령시"),
            RegionEntity(156L, 2, 12L, "아산시"),
            RegionEntity(157L, 2, 12L, "서산시"),
            RegionEntity(158L, 2, 12L, "논산시"),
            RegionEntity(159L, 2, 12L, "계룡시"),
            RegionEntity(160L, 2, 12L, "당진시"),
            RegionEntity(161L, 2, 12L, "금산군"),
            RegionEntity(162L, 2, 12L, "부여군"),
            RegionEntity(163L, 2, 12L, "서천군"),
            RegionEntity(164L, 2, 12L, "청양군"),
            RegionEntity(165L, 2, 12L, "홍성군"),
            RegionEntity(166L, 2, 12L, "예산군"),
            RegionEntity(167L, 2, 12L, "태안군"),
            
            // Level 2: 전북특별자치도 (14개)
            RegionEntity(168L, 2, 13L, "전주시"),
            RegionEntity(169L, 2, 13L, "군산시"),
            RegionEntity(170L, 2, 13L, "익산시"),
            RegionEntity(171L, 2, 13L, "정읍시"),
            RegionEntity(172L, 2, 13L, "남원시"),
            RegionEntity(173L, 2, 13L, "김제시"),
            RegionEntity(174L, 2, 13L, "완주군"),
            RegionEntity(175L, 2, 13L, "진안군"),
            RegionEntity(176L, 2, 13L, "무주군"),
            RegionEntity(177L, 2, 13L, "장수군"),
            RegionEntity(178L, 2, 13L, "임실군"),
            RegionEntity(179L, 2, 13L, "순창군"),
            RegionEntity(180L, 2, 13L, "고창군"),
            RegionEntity(181L, 2, 13L, "부안군"),
            
            // Level 2: 전라남도 (22개)
            RegionEntity(182L, 2, 14L, "목포시"),
            RegionEntity(183L, 2, 14L, "여수시"),
            RegionEntity(184L, 2, 14L, "순천시"),
            RegionEntity(185L, 2, 14L, "나주시"),
            RegionEntity(186L, 2, 14L, "광양시"),
            RegionEntity(187L, 2, 14L, "담양군"),
            RegionEntity(188L, 2, 14L, "곡성군"),
            RegionEntity(189L, 2, 14L, "구례군"),
            RegionEntity(190L, 2, 14L, "고흥군"),
            RegionEntity(191L, 2, 14L, "보성군"),
            RegionEntity(192L, 2, 14L, "화순군"),
            RegionEntity(193L, 2, 14L, "장흥군"),
            RegionEntity(194L, 2, 14L, "강진군"),
            RegionEntity(195L, 2, 14L, "해남군"),
            RegionEntity(196L, 2, 14L, "영암군"),
            RegionEntity(197L, 2, 14L, "무안군"),
            RegionEntity(198L, 2, 14L, "함평군"),
            RegionEntity(199L, 2, 14L, "영광군"),
            RegionEntity(200L, 2, 14L, "장성군"),
            RegionEntity(201L, 2, 14L, "완도군"),
            RegionEntity(202L, 2, 14L, "진도군"),
            RegionEntity(203L, 2, 14L, "신안군"),
            
            // Level 2: 경상북도 (23개)
            RegionEntity(204L, 2, 15L, "포항시"),
            RegionEntity(205L, 2, 15L, "경주시"),
            RegionEntity(206L, 2, 15L, "김천시"),
            RegionEntity(207L, 2, 15L, "안동시"),
            RegionEntity(208L, 2, 15L, "구미시"),
            RegionEntity(209L, 2, 15L, "영주시"),
            RegionEntity(210L, 2, 15L, "영천시"),
            RegionEntity(211L, 2, 15L, "상주시"),
            RegionEntity(212L, 2, 15L, "문경시"),
            RegionEntity(213L, 2, 15L, "경산시"),
            RegionEntity(214L, 2, 15L, "군위군"),
            RegionEntity(215L, 2, 15L, "의성군"),
            RegionEntity(216L, 2, 15L, "청송군"),
            RegionEntity(217L, 2, 15L, "영양군"),
            RegionEntity(218L, 2, 15L, "영덕군"),
            RegionEntity(219L, 2, 15L, "청도군"),
            RegionEntity(220L, 2, 15L, "고령군"),
            RegionEntity(221L, 2, 15L, "성주군"),
            RegionEntity(222L, 2, 15L, "칠곡군"),
            RegionEntity(223L, 2, 15L, "예천군"),
            RegionEntity(224L, 2, 15L, "봉화군"),
            RegionEntity(225L, 2, 15L, "울진군"),
            RegionEntity(226L, 2, 15L, "울릉군"),
            
            // Level 2: 경상남도 (18개)
            RegionEntity(227L, 2, 16L, "창원시"),
            RegionEntity(228L, 2, 16L, "진주시"),
            RegionEntity(229L, 2, 16L, "통영시"),
            RegionEntity(230L, 2, 16L, "사천시"),
            RegionEntity(231L, 2, 16L, "김해시"),
            RegionEntity(232L, 2, 16L, "밀양시"),
            RegionEntity(233L, 2, 16L, "거제시"),
            RegionEntity(234L, 2, 16L, "양산시"),
            RegionEntity(235L, 2, 16L, "의령군"),
            RegionEntity(236L, 2, 16L, "함안군"),
            RegionEntity(237L, 2, 16L, "창녕군"),
            RegionEntity(238L, 2, 16L, "고성군"),
            RegionEntity(239L, 2, 16L, "남해군"),
            RegionEntity(240L, 2, 16L, "하동군"),
            RegionEntity(241L, 2, 16L, "산청군"),
            RegionEntity(242L, 2, 16L, "함양군"),
            RegionEntity(243L, 2, 16L, "거창군"),
            RegionEntity(244L, 2, 16L, "합천군"),
            
            // Level 2: 제주특별자치도 (2개)
            RegionEntity(245L, 2, 17L, "제주시"),
            RegionEntity(246L, 2, 17L, "서귀포시")
        )
    }
}

