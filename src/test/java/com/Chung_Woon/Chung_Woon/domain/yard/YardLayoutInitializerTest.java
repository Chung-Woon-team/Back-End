package com.Chung_Woon.Chung_Woon.domain.yard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 기동하면 도면대로 깔려 있는지. */
@SpringBootTest
class YardLayoutInitializerTest {

	@Autowired
	private BlockRepository blockRepository;

	@Autowired
	private SlotRepository slotRepository;

	@Test
	@DisplayName("블록 4개와 슬롯 1,936개가 적재된다")
	void layoutIsSeeded() {
		assertThat(blockRepository.count()).isEqualTo(YardGrid.BLOCK_COUNT);
		assertThat(slotRepository.count()).isEqualTo(YardGrid.SLOT_COUNT);
		assertThat(slotRepository.findByBlock_BlockId("B01")).hasSize(YardGrid.SLOTS_PER_BLOCK);

		Slot corner = slotRepository.findById("B01-R04-C04").orElseThrow();
		assertThat(corner.getGridRow()).isEqualTo(4);
		assertThat(corner.getGridCol()).isEqualTo(4);
		assertThat(corner.getLane()).isZero();
		assertThat(corner.getDepth()).isZero();
		assertThat(corner.getAccessSide()).isEqualTo(AccessSide.NORTH);
		assertThat(corner.getStatus()).isEqualTo(SlotStatus.EMPTY);
	}

	@Test
	@DisplayName("적재 직후에는 모든 슬롯이 배치 가능하다")
	void allSlotsAssignableAtStart() {
		assertThat(slotRepository.findAssignable()).hasSize(YardGrid.SLOT_COUNT);
	}
}
