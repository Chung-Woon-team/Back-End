package com.Chung_Woon.Chung_Woon.domain.billoflading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillOfLadingRepository extends JpaRepository<BillOfLading, String> {

	List<BillOfLading> findByVoyageNumber(String voyageNumber);

	List<BillOfLading> findByLotCode(String lotCode);

	/** 하선 순번대로. 같은 항차의 작업 순서를 잡을 때 쓴다. */
	List<BillOfLading> findByVoyageNumberOrderByDischargeSeqFromAsc(String voyageNumber);
}
