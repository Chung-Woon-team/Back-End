package com.Chung_Woon.Chung_Woon.domain.yard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, String> {

	List<Slot> findByBlock_BlockId(String blockId);

	List<Slot> findByStatus(SlotStatus status);

	/** 배치 가능한 슬롯 = 비어 있고, 속한 블록이 폐쇄되지 않은 것. */
	@Query("select s from Slot s join s.block b where s.status = 'EMPTY' and b.closed = false")
	List<Slot> findAssignable();

	/** 배치 가능한 자리 수. 대시보드가 1,936칸을 전부 들고오지 않게 카운트만 센다. */
	@Query("select count(s) from Slot s join s.block b where s.status = 'EMPTY' and b.closed = false")
	long countAssignable();

	long countByStatus(SlotStatus status);

	/** 블록별 점유 수. [blockId, count] 로 나온다. */
	@Query("select s.block.blockId, count(s) from Slot s where s.status = :status group by s.block.blockId")
	List<Object[]> countByStatusGroupedByBlock(SlotStatus status);
}
