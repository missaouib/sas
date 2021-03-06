package com.js.sas.entity.dto;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @ClassName SaleAmountEntity
 * @Description 周期销售金额，日、月、年。
 * @Author zc
 * @Date 2019/6/21 11:53
 **/
@Entity
@Data
public class SaleAmountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private int id;

    // 总金额
    private BigDecimal amount;

    // 总数量
    private BigDecimal counts;

    // 日
    private String days;

    // 月
    private String months;

    // 年
    private String years;
}
