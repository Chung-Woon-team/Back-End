package com.Chung_Woon.Chung_Woon.domain.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

	/** 한 판의 전체 배치를 차량·슬롯까지 한 번에 읽는다 (N+1 방지). */
	@Query("""
			select p from Placement p
			join fetch p.vehicle
			join fetch p.slot s
			join fetch s.block
			where p.planRevision.planVersion = :planVersion
			""")
	List<Placement> findAllByPlanVersion(String planVersion);
}
