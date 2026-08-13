package com.Chung_Woon.Chung_Woon.domain.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ObservationSnapshotRepository extends JpaRepository<ObservationSnapshot, Long> {

	/** 그 블록의 가장 최근 관측. "관측 상태 최신성 표시"(장표 7쪽 보조기능)에 쓴다. */
	Optional<ObservationSnapshot> findFirstByBlock_BlockIdOrderByCapturedAtDesc(String blockId);
}
