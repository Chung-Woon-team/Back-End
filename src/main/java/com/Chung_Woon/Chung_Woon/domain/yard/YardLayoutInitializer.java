package com.Chung_Woon.Chung_Woon.domain.yard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 도면대로 블록 4개와 슬롯 1,936개를 깔아 둔다.
 *
 * <p>블록이 하나라도 있으면 아무것도 하지 않는다. local 프로필은 매번 새 H2 라 매 기동마다 깔리고,
 * 운영 DB 는 최초 1회만 깔린다. 도면이 바뀌면 이 코드가 아니라 마이그레이션으로 옮겨야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YardLayoutInitializer implements ApplicationRunner {

	private final BlockRepository blockRepository;
	private final SlotRepository slotRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (blockRepository.count() > 0) {
			log.info("야드 격자가 이미 있다. 초기 적재를 건너뛴다. (블록 {}개, 슬롯 {}개)",
					blockRepository.count(), slotRepository.count());
			return;
		}

		List<Slot> slots = new ArrayList<>(YardGrid.SLOT_COUNT);
		for (BlockLayout layout : BlockLayout.values()) {
			Block block = blockRepository.save(Block.from(layout));
			for (int row = layout.originRow(); row <= layout.lastRow(); row++) {
				for (int col = layout.originCol(); col <= layout.lastCol(); col++) {
					slots.add(Slot.at(block, row, col));
				}
			}
		}
		slotRepository.saveAll(slots);

		log.info("야드 격자 {}×{} 적재 완료 — 블록 {}개, 슬롯 {}개, 도로 {}칸",
				YardGrid.SIZE, YardGrid.SIZE, BlockLayout.values().length, slots.size(),
				YardGrid.ROAD_CELL_COUNT);
	}
}
