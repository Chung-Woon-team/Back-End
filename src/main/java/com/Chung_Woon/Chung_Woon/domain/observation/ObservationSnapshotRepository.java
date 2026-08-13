package com.Chung_Woon.Chung_Woon.domain.observation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ObservationSnapshotRepository extends JpaRepository<ObservationSnapshot, Long> {

	/** 그 블록의 가장 최근 관측. "관측 상태 최신성 표시"(장표 7쪽 보조기능)에 쓴다. */
	Optional<ObservationSnapshot> findFirstByBlock_BlockIdOrderByCapturedAtDesc(String blockId);

	/** 사진 한 장에서 나온 블록별 관측 4행을 한 번에. 점유 확인 흐름의 2단계가 이걸로 되짚는다. */
	@Query("select o from ObservationSnapshot o join fetch o.block where o.batchId = :batchId")
	List<ObservationSnapshot> findAllByBatchId(String batchId);
}
