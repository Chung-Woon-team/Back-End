package com.Chung_Woon.Chung_Woon.domain.yard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, String> {

	List<Block> findByClosedFalse();
}
