package com.js.sas.controller;

import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.event.WriteHandler;
import com.alibaba.excel.metadata.Sheet;
import com.alibaba.excel.metadata.Table;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.js.sas.dto.OverdueDTO;
import com.js.sas.dto.SettlementSummaryDTO;
import com.js.sas.entity.*;
import com.js.sas.entity.Dictionary;
import com.js.sas.repository.DeptStaffRepository;
import com.js.sas.repository.MemberSalemanRepository;
import com.js.sas.service.DictionaryService;
import com.js.sas.service.FinanceService;
import com.js.sas.service.PartnerService;
import com.js.sas.utils.*;
import com.js.sas.utils.constant.ExcelPropertyEnum;
import com.js.sas.utils.upload.ExcelListener;
import com.js.sas.utils.upload.UploadData;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.zip.ZipOutputStream;

/**
 * @ClassName FinanceController
 * @Description 财务Controller
 * @Author zc
 * @Date 2019/6/11 12:47
 **/
@RestController
@Slf4j
@RequestMapping("/finance")
public class FinanceController {

    @Value("${yongyou.url}")
    private String url;

    private final FinanceService financeService;

    private final DictionaryService dictionaryService;

    private final PartnerService partnerService;

    private final DataSource dataSource;

    @Autowired
    private DeptStaffRepository deptStaffRepository;

    @Autowired
    private MemberSalemanRepository memberSalemanRepository;

    public FinanceController(FinanceService financeService, DictionaryService dictionaryService, DataSource dataSource, PartnerService partnerService) {
        this.financeService = financeService;
        this.dictionaryService = dictionaryService;
        this.dataSource = dataSource;
        this.partnerService = partnerService;
    }

    /**
     * 目前调用存储过程实现，后期需要修改实现方法。
     *
     * @param settlementSummasryDTO 结算客户汇总DTO
     * @param bindingResult         校验结果
     * @return Object
     */
    @ApiOperation(value = "结算客户汇总（线上、线下）", notes = "数据来源：用友；数据截止日期：昨天")
    @PostMapping(value = "/settlementSummary")
    public Object settlementSummary(@Validated SettlementSummaryDTO settlementSummasryDTO, BindingResult bindingResult) {
        // 参数格式校验
        Result checkResult = CommonUtils.checkParameter(bindingResult);
        if (checkResult != null) {
            return checkResult;
        }

        return financeService.getSettlementSummary(settlementSummasryDTO.getName(),
                settlementSummasryDTO.getChannel(),
                settlementSummasryDTO.getStartDate(),
                settlementSummasryDTO.getEndDate(),
                settlementSummasryDTO.getOffset(),
                settlementSummasryDTO.getLimit(),
                settlementSummasryDTO.getSort(),
                settlementSummasryDTO.getSortOrder());
    }

    /**
     * 下载结算客户对账单
     *
     * @param name                结算客户名称
     * @param channel             渠道
     * @param startDate           开始时间
     * @param endDate             结束时间
     * @param limit               数量
     * @param httpServletResponse httpServletResponse
     */
    @ApiIgnore
    @PostMapping("/settlementSummary/download/excel")
    public void downloadSettlementSummary(String name, String channel, String startDate, String endDate, String limit, HttpServletResponse httpServletResponse) {
        List<SettlementSummaryEntity> settlementSummaryList = (List<SettlementSummaryEntity>) financeService.getSettlementSummary(
                name,
                channel,
                startDate,
                endDate,
                0,
                Integer.parseInt(limit),
                "name",
                "asc").get("rows");
        try {
            CommonUtils.export(httpServletResponse, settlementSummaryList, "结算客户汇总（线上、线下）", new SettlementSummaryEntity());
        } catch (IOException e) {
            log.error("下载结算客户汇总（线上、线下）异常：{}", e);
            e.printStackTrace();
        }
    }

    @ApiOperation(value = "现金客户逾期统计", notes = "数据来源：用友；数据截止日期：昨天")
    @PostMapping("/overdue")
    public Object overdue(@Validated OverdueDTO partner, BindingResult bindingResult) {
        // 参数格式校验
        Result checkResult = CommonUtils.checkParameter(bindingResult);
        if (checkResult != null) {
            return checkResult;
        }
        HashMap<String, Object> result = new HashMap<>();
        Page page = financeService.findOverdue(partner);
        result.put("rows", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    /**
     * 逾期现金客户导出
     *
     * @param limit               数量
     * @param httpServletResponse httpServletResponse
     */
    @ApiIgnore
    @PostMapping("/overdueCash/download/excel")
    public void downloadOverdueCash(int limit, HttpServletResponse httpServletResponse) {
        OverdueDTO overdueDTO = new OverdueDTO();
        overdueDTO.setLimit(limit);
        overdueDTO.setSort("name");
        overdueDTO.setSortOrder("asc");
        overdueDTO.setStatus("0");
        overdueDTO.setSettlementType("1");
        overdueDTO.setParentCode("0");

        List overdueList = financeService.findOverdue(overdueDTO).getContent();
        try {
            CommonUtils.export(httpServletResponse, overdueList, "现金客户逾期统计", new PartnerEntity());
        } catch (IOException e) {
            log.error("下载现金客户逾期统计异常：{}", e);
            e.printStackTrace();
        }
    }


    /**
     * 账期逾期客户导出
     * 导出表格内容截止到当前月份，动态表格直接调用存储过程实现。
     * 业务逻辑很复杂
     *
     * @param httpServletResponse httpServletResponse
     */
    @ApiIgnore
    @PostMapping("/overdueCredit/download/excel")
    public void downloadOverdueCredit(HttpServletResponse httpServletResponse) {
        financeService.downloadOverdueCredit(httpServletResponse);
    }

    /**
     * 更新逾期数据
     *
     * @return result
     */
    @PostMapping("/refreshOverdueData")
    public Result refreshOverdueData() {
        String result = new RemoteShellExecutor("192.168.8.164", 22, "root", "root", "sudo /usr/local/pentaho/cronjobs/001.sh").exec();
        return ResultUtils.getResult(ResultCode.成功, result);
    }

    /**
     * 查询逾期数据更新时间
     *
     * @return 逾期数据更新时间
     */
    @PostMapping("/findeOverdueRefreshTime")
    public Result findeOverdueRefreshTime() {
        List<Dictionary> dictionaryList = dictionaryService.findByCode("001");
        if (!dictionaryList.isEmpty()) {
            return ResultUtils.getResult(ResultCode.成功, dictionaryList.get(0).getValue());
        } else {
            return ResultUtils.getResult(ResultCode.系统异常);
        }
    }


    /**
     * 用友对账单
     *
     * @param period 账期
     * @param name   对账单位名称
     * @return Result
     */
    @PostMapping("/findYonyouStatement")
    public Result findYonyouStatement(String period, String name) {
        if (StringUtils.isBlank(period) || StringUtils.isBlank(name)) {
            return ResultUtils.getResult(ResultCode.参数错误);
        }
        String startDate;
        String endDate;
        String[] dateArray = period.split("-");
        if (dateArray.length == 2) {
            if (CommonUtils.isNumber(dateArray[0]) && CommonUtils.isNumber(dateArray[1])) {
                if (Integer.parseInt(dateArray[1]) > 1 && Integer.parseInt(dateArray[1]) <= 12) {
                    startDate = dateArray[0] + "-" + (Integer.parseInt(dateArray[1]) - 1) + "-28";
                    endDate = period + "-27";
                } else if (Integer.parseInt(dateArray[1]) == 1) {
                    startDate = (Integer.parseInt(dateArray[0]) - 1) + "-12-28";
                    endDate = period + "-27";
                } else {
                    return ResultUtils.getResult(ResultCode.参数错误);
                }
            } else {
                return ResultUtils.getResult(ResultCode.参数错误);
            }
        } else {
            return ResultUtils.getResult(ResultCode.参数错误);
        }

        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("startDate", startDate);
        multiValueMap.add("endDate", endDate);
        multiValueMap.add("settleCustomer", name);

        ResponseEntity responseEntity = CommonUtils.sendPostRequest(url, multiValueMap);

        return ResultUtils.getResult(ResultCode.成功, responseEntity.getBody());
    }

    /**
     * 导出用友对账单
     *
     * @param response HttpServletResponse
     * @param period   账期
     * @param name     对账单位名称
     */
    @ApiIgnore
    @PostMapping("/exportYonyouStatement")
    public void exportYonyouStatement(HttpServletResponse response, String period, String name) {
        EnumMap<ExcelPropertyEnum, Object> enumMap = financeService.getYonyouStatementExcel(period, name);

        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        response.setContentType("multipart/form-data");
        response.setCharacterEncoding("utf-8");
        try {
            response.setHeader("Content-Disposition", "attachment;filename*= UTF-8''" + URLEncoder.encode(enumMap.get(ExcelPropertyEnum.FILENAME).toString(), "UTF-8") + ".xlsx");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        WriteHandler handler = (WriteHandler) enumMap.get(ExcelPropertyEnum.HANDLER);
        // 写入数据
        ExcelWriter writer = new ExcelWriter(null, out, ExcelTypeEnum.XLSX, true, handler);
        writer.write1((List) enumMap.get(ExcelPropertyEnum.ROWLIST), (Sheet) enumMap.get(ExcelPropertyEnum.SHEET));
        // 合并单元格
        writer.merge(1, 1, 0, 2);
        writer.merge(1, 1, 3, 4);
        writer.merge(1, 1, 5, 6);
        writer.merge(2, 2, 0, 2);
        writer.merge(2, 2, 3, 4);
        writer.merge(2, 2, 5, 6);
        writer.merge(4, 4, 0, 7);
        List<Integer> mergeRowNumList = (List<Integer>) enumMap.get(ExcelPropertyEnum.MERGE);
        for (int index : mergeRowNumList) {
            writer.merge(index, index, 0, 2);
        }
        if (!mergeRowNumList.isEmpty()) {
            int index = mergeRowNumList.get(mergeRowNumList.size() - 1);
            writer.merge(index + 4, index + 4, 0, 2);
            writer.merge(index + 5, index + 5, 0, 2);
            writer.merge(index + 6, index + 6, 0, 2);
            writer.merge(index + 7, index + 7, 0, 2);
            writer.merge(index + 3, index + 3, 0, 7);

            int first = mergeRowNumList.get(0);
            writer.merge(first + 3, first + 3, 0, 7);
        }
        // 关闭
        writer.finish();
        try {
            if (out != null) {
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 导出全部用友对账单，并打包ZIP文件
     *
     * @param response
     * @param period
     */
    @ApiIgnore
    @PostMapping("/exportAllYonyouStatement")
    public void exportAllYonyouStatement(HttpServletResponse response, String period) {
        // 判断参数
        if (StringUtils.isBlank(period)) {
            return;
        }
        log.info("导出全部用友对账单");
        // 输出流
        OutputStream out = null;
        // 获取往来单位List
        List<PartnerEntity> partnerList = partnerService.findAllNameListOrderByName();
        // 导出时间格式化
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        // 导出压缩文件名称
        String zipFileName = df.format(new Date());
        // 导出文件夹
        File sourceFile = new File("/usr/local/project/sas/" + zipFileName);
        if (!sourceFile.exists()) {
            sourceFile.mkdirs();
        }

        // 遍历往来单位
        for (PartnerEntity partner : partnerList) {
            EnumMap<ExcelPropertyEnum, Object> enumMap = financeService.getYonyouStatementExcel(period, partner.getName());
            if (enumMap == null) {
                log.warn("获取接口数据错误，单位名称：" + partner.getName() + "，账期：" + period);
                continue;
            }
            try {
                out = new FileOutputStream(sourceFile.getPath() + "/" + enumMap.get(ExcelPropertyEnum.FILENAME).toString() + ".xlsx");
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 写入数据
            ExcelWriter writer = new ExcelWriter(null, out, ExcelTypeEnum.XLSX, true, (WriteHandler) enumMap.get(ExcelPropertyEnum.HANDLER));
            writer.write1((List) enumMap.get(ExcelPropertyEnum.ROWLIST), (Sheet) enumMap.get(ExcelPropertyEnum.SHEET));
            // 合并单元格
            writer.merge(1, 1, 0, 2);
            writer.merge(1, 1, 3, 4);
            writer.merge(1, 1, 5, 6);
            writer.merge(2, 2, 0, 2);
            writer.merge(2, 2, 3, 4);
            writer.merge(2, 2, 5, 6);
            writer.merge(4, 4, 0, 7);
            List<Integer> mergeRowNumList = (List<Integer>) enumMap.get(ExcelPropertyEnum.MERGE);
            for (int index : mergeRowNumList) {
                writer.merge(index, index, 0, 2);
            }
            if (!mergeRowNumList.isEmpty()) {
                int index = mergeRowNumList.get(mergeRowNumList.size() - 1);
                writer.merge(index + 4, index + 4, 0, 2);
                writer.merge(index + 5, index + 5, 0, 2);
                writer.merge(index + 6, index + 6, 0, 2);
                writer.merge(index + 7, index + 7, 0, 2);
                writer.merge(index + 3, index + 3, 0, 7);

                int first = mergeRowNumList.get(0);
                writer.merge(first + 3, first + 3, 0, 7);
            }
            // 关闭
            writer.finish();
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=" + zipFileName + ".zip");
        // 压缩
        ServletOutputStream servletOutputStream = null;
        try {
            servletOutputStream = response.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(servletOutputStream);
            CommonUtils.compress(sourceFile, zos, zipFileName, true);
            log.info("导出全部用友对账单-压缩完成");
        } catch (Exception e) {
            throw new RuntimeException("用友对账单批量导出压缩异常", e);
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @ApiIgnore
    @PostMapping("/overdueAllColumns")
    public Object overdueAllColumns() {
        return financeService.findOverdueAllColumns();
    }

    @ApiOperation(value = "全部客户逾期统计", notes = "数据来源：用友；数据截止日期：昨天")
    @PostMapping("/overdueAll")
    public Object overdueAll(@Validated OverdueDTO partner, BindingResult bindingResult) {
        // 参数格式校验
        Result checkResult = CommonUtils.checkParameter(bindingResult);
        if (checkResult != null) {
            return checkResult;
        }
        HashMap<String, Object> result = new HashMap<>();
        result.put("rows", financeService.findOverdueAllData(partner).get("map"));
        result.put("total", financeService.findOverdueAllCount(partner));
        return result;
    }

    /**
     * 全部客户逾期统计导出
     *
     * @param httpServletResponse HttpServletResponse
     * @throws IOException IOException
     */
    @ApiIgnore
    @PostMapping("/exportOverdueAll")
    public void exportOverdueAll(HttpServletResponse httpServletResponse) throws IOException {
        // 列名
        List<String> columnsList = financeService.findOverdueAllColumns().get("columns");
        // 数据
        List<List<Object>> rowsList = (List<List<Object>>) financeService.findOverdueAllData(null).get("list");

        /**
         * 20191226：关联客户最下面添加一行小计金额
         */
        String lastParentName = "";
        // 应收
        BigDecimal totalReceivables = BigDecimal.ZERO;
        // 逾期
        BigDecimal overdue = BigDecimal.ZERO;
        // 计数器
        int num = 0;

        for (int index = 0; index < rowsList.size(); index++) {
            if (index == 0) {
                lastParentName = rowsList.get(index).get(6).toString();
            }
            // 订货客户
            if (lastParentName.equals(rowsList.get(index).get(6).toString())) {
                totalReceivables = totalReceivables.add(new BigDecimal(rowsList.get(index).get(7).toString()));
                overdue = overdue.add(new BigDecimal(rowsList.get(index).get(8).toString()));
                num++;
            } else {
                lastParentName = rowsList.get(index).get(6).toString();
                if (num > 1) {
                    List dataList = new ArrayList();
                    dataList.add("小计");
                    dataList.add("");
                    dataList.add("");
                    dataList.add("");
                    dataList.add("");
                    dataList.add("");
                    dataList.add("");
                    dataList.add(totalReceivables);
                    dataList.add(overdue);
                    rowsList.add(index, dataList);

                    totalReceivables = BigDecimal.ZERO;
                    overdue = BigDecimal.ZERO;
                } else {
                    totalReceivables = BigDecimal.ZERO;
                    overdue = BigDecimal.ZERO;
                    totalReceivables = totalReceivables.add(new BigDecimal(rowsList.get(index).get(7).toString()));
                    overdue = overdue.add(new BigDecimal(rowsList.get(index).get(8).toString()));
                }
                num = 0;
            }
        }

        String fileName = "逾期统计表";
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");

        Sheet sheet1 = new Sheet(1, 0);
        sheet1.setSheetName(fileName);
        sheet1.setAutoWidth(Boolean.TRUE);

        fileName = fileName + df.format(new Date());
        ServletOutputStream out = httpServletResponse.getOutputStream();
        httpServletResponse.setContentType("multipart/form-data");
        httpServletResponse.setCharacterEncoding("utf-8");
        httpServletResponse.setHeader("Content-Disposition", "attachment;filename*= UTF-8''" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");
        // 设置列名
        if (columnsList != null) {
            List<List<String>> list = new ArrayList<>();
            columnsList.forEach(c -> list.add(Collections.singletonList(c)));
            sheet1.setHead(list);
        }
        // 写入数据
        ExcelWriter writer = new ExcelWriter(out, ExcelTypeEnum.XLSX, true);
        writer.write1(rowsList, sheet1);

        writer.finish();
        out.flush();
        out.close();
    }

    @ApiIgnore
    @PostMapping("/overdueSalesColumns")
    public Object overdueSalesColumns() {
        return financeService.findOverdueSalesColumns();
    }

    @ApiOperation(value = "客户逾期统计（销售版本）", notes = "数据来源：用友；数据截止日期：昨天")
    @PostMapping("/overdueSales")
    public Object overdueSales(@Validated OverdueDTO partner, BindingResult bindingResult) {
        // 参数格式校验
        Result checkResult = CommonUtils.checkParameter(bindingResult);
        if (checkResult != null) {
            return checkResult;
        }
        // 列名
        List<String> columnsList = financeService.findOverdueSalesColumns().get("columns");

        List<List<Object>> objectRowsList = financeService.getOverdueSalesList(partner);

        // 数据
        ArrayList<Map<String, Object>> rowsList = new ArrayList<>();

        for (List<Object> objectList : objectRowsList) {
            Map<String, Object> dataMap = new HashMap<>();

            // 去掉-数据，需要优化
            for (int index = 0; index < objectList.size(); index++) {
                if (objectList.get(index).toString().equals("-")) {
                    objectList.remove(index--);
                }
            }

            // 设置数据列
            // 前面固定部分和后面月份动态部门分别处理
            for (int index = 0; index < 10; index++) {
                dataMap.put(columnsList.get(index), objectList.get(index));
            }
            // 动态部分
            for (int index = 1; index <= columnsList.size() - 10; index++) {
                if (index > 3) {
                    dataMap.put(columnsList.get(columnsList.size() - index), objectList.get(objectList.size() - index));
                } else {
                    // 按账期日，每5天划分一列，向后合并。例如：账期日7显示在10日列。
                    // 为了对应bootstrap的列名，看不懂可以重新写，符合规则就行。
                    int date = 0;
                    if (StringUtils.isNumeric(dataMap.get("账期日").toString())) {
                        date = Integer.parseInt(dataMap.get("账期日").toString());
                    }
                    if (date <= 5) {
                        dataMap.put((4 - index) + "05", objectList.get(objectList.size() - index));
                    } else if (date <= 10) {
                        dataMap.put((4 - index) + "10", objectList.get(objectList.size() - index));
                    } else if (date <= 15) {
                        dataMap.put((4 - index) + "15", objectList.get(objectList.size() - index));
                    } else if (date <= 20) {
                        dataMap.put((4 - index) + "20", objectList.get(objectList.size() - index));
                    } else if (date <= 25) {
                        dataMap.put((4 - index) + "25", objectList.get(objectList.size() - index));
                    } else {
                        dataMap.put((4 - index) + "30", objectList.get(objectList.size() - index));
                    }
                }
            }

            rowsList.add(dataMap);
        }

        HashMap<String, Object> result = new HashMap<>();
        result.put("rows", rowsList);
        result.put("total", financeService.findOverdueAllCount(partner));
        return result;
    }

    @ApiIgnore
    @PostMapping("/exportOverdueSales")
    public void exportOverdueSales(HttpServletResponse httpServletResponse) throws IOException {
        String fileName = "逾期统计表";
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        List<String> columnsList = financeService.findOverdueSalesColumns().get("columns");
        // 表单
        Sheet sheet = new Sheet(1, 0);
        sheet.setSheetName(fileName);
        // 创建一个表格
        Table table = new Table(1);
        // 表头List
        List<List<String>> headList = new ArrayList<>();
        // 拼接表头
        for (int index = 0; index < columnsList.size(); index++) {
            if (index < 10) {
                List<String> headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add(columnsList.get(index));
                headList.add(headTitle);
            } else {
                List<String> headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("05");
                headList.add(headTitle);
                headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("10");
                headList.add(headTitle);
                headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("15");
                headList.add(headTitle);
                headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("20");
                headList.add(headTitle);
                headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("25");
                headList.add(headTitle);
                headTitle = new ArrayList<>();
                headTitle.add(columnsList.get(index));
                headTitle.add("30");
                headList.add(headTitle);
            }
        }

        table.setHead(headList);

        Sheet sheet1 = new Sheet(1, 0);
        sheet1.setSheetName(fileName);
        sheet1.setAutoWidth(Boolean.TRUE);

        fileName = fileName + df.format(new Date());
        ServletOutputStream out = httpServletResponse.getOutputStream();
        httpServletResponse.setContentType("multipart/form-data");
        httpServletResponse.setCharacterEncoding("utf-8");
        httpServletResponse.setHeader("Content-Disposition", "attachment;filename*= UTF-8''" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");
        // 设置列名
        if (columnsList != null) {
            List<List<String>> list = new ArrayList<>();
            columnsList.forEach(c -> list.add(Collections.singletonList(c)));
            sheet1.setHead(list);
        }

        /**
         * 以下处理数据
         */
        // 数据List
        List<Object[]> resultDataList = financeService.findOverdueSales(null);
        // 数据
        List<List<Object>> rowsList = financeService.getOverdueSalesList(null);

        /**
         * 20191226：关联客户最下面添加一行小计金额
         */
        String lastParentName = "";
        // 应收
        BigDecimal totalReceivables = BigDecimal.ZERO;
        // 逾期
        BigDecimal totalOverdue = BigDecimal.ZERO;
        // 期初应收
        BigDecimal totalOpeningBalance = BigDecimal.ZERO;

        BigDecimal tatalAmount1 = BigDecimal.ZERO;
        BigDecimal tatalAmount2 = BigDecimal.ZERO;
        BigDecimal tatalAmount3 = BigDecimal.ZERO;

        // 计数器
        int num = 0;

        for (int index = 0; index < rowsList.size(); index++) {
            if (index == 0) {
                lastParentName = rowsList.get(index).get(6).toString();
            }
            // 订货客户
            if (lastParentName.equals(rowsList.get(index).get(6).toString())) {
                totalReceivables = totalReceivables.add(new BigDecimal(rowsList.get(index).get(7).toString()));
                totalOverdue = totalOverdue.add(new BigDecimal(rowsList.get(index).get(8).toString()));
                totalOpeningBalance = totalOpeningBalance.add(new BigDecimal(rowsList.get(index).get(9).toString()));

                int monthDataIndex = 1;

                for (int dataIndex = 10; dataIndex < 28; dataIndex++) {
                    // 不等于-，说明是结算日
                    if (!rowsList.get(index).get(dataIndex).toString().equals("-")) {
                        if (monthDataIndex == 1) {
                            tatalAmount1 = tatalAmount1.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                            monthDataIndex++;
                        } else if (monthDataIndex == 2) {
                            tatalAmount2 = tatalAmount2.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                            monthDataIndex++;
                        } else if (monthDataIndex == 3) {
                            tatalAmount3 = tatalAmount3.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                            monthDataIndex++;
                        }
                    }
                }
                num++;
            } else {
                if (num > 1) {
                    List innerDataList = new ArrayList();
                    innerDataList.add("");
                    innerDataList.add("");
                    innerDataList.add("");
                    innerDataList.add("");
                    innerDataList.add("");
                    innerDataList.add("");
                    innerDataList.add(lastParentName + " 小计");
                    innerDataList.add(totalReceivables);
                    innerDataList.add(totalOverdue);
                    innerDataList.add(totalOpeningBalance);

                    int monthDataIndex = 1;
                    //
                    for (int dataIndex = 10; dataIndex < 28; dataIndex++) {
                        // 不等于-，说明是结算日
                        if (index == 0) {
                            continue;
                        }
                        if (!rowsList.get(index - 1).get(dataIndex).toString().equals("-")) {
                            if (monthDataIndex == 1) {
                                innerDataList.add(tatalAmount1);
                                monthDataIndex++;
                            } else if (monthDataIndex == 2) {
                                innerDataList.add(tatalAmount2);
                                monthDataIndex++;
                            } else if (monthDataIndex == 3) {
                                innerDataList.add(tatalAmount3);
                                monthDataIndex++;
                            }
                        } else {
                            innerDataList.add("-");
                        }
                    }

                    rowsList.add(index, innerDataList);

                    totalReceivables = BigDecimal.ZERO;
                    totalOverdue = BigDecimal.ZERO;
                    totalOpeningBalance = BigDecimal.ZERO;
                    tatalAmount1 = BigDecimal.ZERO;
                    tatalAmount2 = BigDecimal.ZERO;
                    tatalAmount3 = BigDecimal.ZERO;
                    num = 0;
                } else {
                    totalReceivables = BigDecimal.ZERO;
                    totalOverdue = BigDecimal.ZERO;
                    totalOpeningBalance = BigDecimal.ZERO;
                    tatalAmount1 = BigDecimal.ZERO;
                    tatalAmount2 = BigDecimal.ZERO;
                    tatalAmount3 = BigDecimal.ZERO;
                    totalReceivables = totalReceivables.add(new BigDecimal(rowsList.get(index).get(7).toString()));
                    totalOverdue = totalOverdue.add(new BigDecimal(rowsList.get(index).get(8).toString()));
                    totalOpeningBalance = totalOpeningBalance.add(new BigDecimal(rowsList.get(index).get(9).toString()));

                    int monthDataIndex = 1;

                    for (int dataIndex = 10; dataIndex < 28; dataIndex++) {
                        // 不等于-，说明是结算日
                        if (!rowsList.get(index).get(dataIndex).toString().equals("-")) {
                            if (monthDataIndex == 1) {
                                tatalAmount1 = tatalAmount1.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                                monthDataIndex++;
                            } else if (monthDataIndex == 2) {
                                tatalAmount2 = tatalAmount2.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                                monthDataIndex++;
                            } else if (monthDataIndex == 3) {
                                tatalAmount3 = tatalAmount3.add(new BigDecimal(rowsList.get(index).get(dataIndex).toString()));
                                monthDataIndex++;
                            }
                        }
                    }

                    num = 1;
                }
                lastParentName = rowsList.get(index).get(6).toString();
            }
        }

        // 写入数据
        ExcelWriter writer = new ExcelWriter(out, ExcelTypeEnum.XLSX, true);
        writer.write1(rowsList, sheet, table);

        writer.finish();
        out.flush();
        out.close();
    }

    /**
     * 查询用友线上供应商应付对账单
     *
     * @param request
     * @return
     */
    @PostMapping(value = "getSupplier")
    public Object getSupplier(HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        try {
            String isOnline = null;
            if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
                requestMap.put("startDate", request.getParameter("startDate"));
            }
            if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
                requestMap.put("endDate", request.getParameter("endDate"));
            }
            if (StringUtils.isNotBlank(request.getParameter("isOnline"))) {
                isOnline = request.getParameter("isOnline");
            } else {
                return null;
            }
            if (StringUtils.isNotBlank(request.getParameter("name"))) {
                requestMap.put("name", request.getParameter("name"));
            } else {
                return new Result("400", "供应商名称为空", null);
            }
            if (StringUtils.isNotBlank(request.getParameter("limit"))) {
                requestMap.put("limit", request.getParameter("limit"));
            } else {
                requestMap.put("limit", "100");
            }
            if (StringUtils.isNotBlank(request.getParameter("offset"))) {
                requestMap.put("offset", request.getParameter("offset"));
            } else {
                requestMap.put("offset", "0");
            }

            List<Map<String, Object>> supplier = this.getSupplier(requestMap, isOnline);
            result.put("rows", supplier);
            result.put("total", financeService.getSupplierCount(requestMap, isOnline));
            return result;
        } catch (ParseException e) {
            return new Result("400", "异常", null);
        }
    }

    @PostMapping(value = "/download/SupplierExcel")
    public void downSupplierExcel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, String> requestMap = new HashMap<>();
        List<List<Object>> result = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
            requestMap.put("startDate", request.getParameter("startDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
            requestMap.put("endDate", request.getParameter("endDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("name"))) {
            requestMap.put("name", request.getParameter("name"));
        } else {
            return;
        }
        requestMap.put("limit", "10000000000");
        requestMap.put("offset", "0");

        List<Map<String, Object>> online = this.getSupplier(requestMap, "线上");
        List<Map<String, Object>> offline = this.getSupplier(requestMap, "线下");
//        financeService.getCountSupplier
        List<String> columnNameList = new ArrayList<>();
        columnNameList.add("日期");
        columnNameList.add("合同编号");
        columnNameList.add("单据类别");
        columnNameList.add("收货金额");
        columnNameList.add("付款金额");
        columnNameList.add("应付账款");
        columnNameList.add("开票金额");
        columnNameList.add("发票结余/开票余额");
        List<Object> objects;
        objects = new ArrayList<>();
        objects.add("线上供应商对账单:");
        result.add(objects);
        for (Map<String, Object> order : online) {
            objects = new ArrayList<>();
            objects.add(order.get("voucherdate"));
            objects.add(order.get("code"));
            objects.add(order.get("type"));
            objects.add(order.get("receivingAmount"));
            objects.add(order.get("paymentAmount"));
            objects.add(order.get("balancePayableAmount"));
            objects.add(order.get("invoiceAmount"));
            objects.add(order.get("balanceInvoiceAmount"));
            result.add(objects);
        }
        result.add(new ArrayList<>());
        objects = new ArrayList<>();
        objects.add("线下供应商对账单:");
        result.add(objects);
        for (Map<String, Object> order : offline) {
            objects = new ArrayList<>();
            objects.add(order.get("voucherdate"));
            objects.add(order.get("code"));
            objects.add(order.get("type"));
            objects.add(order.get("receivingAmount"));
            objects.add(order.get("paymentAmount"));
            objects.add(order.get("balancePayableAmount"));
            objects.add(order.get("invoiceAmount"));
            objects.add(order.get("balanceInvoiceAmount"));
            result.add(objects);
        }
        List<Map<String, Object>> mapList = financeService.getSupplierCount(requestMap);
        if (mapList != null && mapList.size() > 0) {
            HashMap<String, Object> map = new HashMap<>();
            BigDecimal balancePayableAmount = BigDecimal.ZERO;
            BigDecimal balanceInvoiceAmount = BigDecimal.ZERO;
            BigDecimal receivingAmount = BigDecimal.ZERO;
            BigDecimal paymentAmount = BigDecimal.ZERO;
            BigDecimal invoiceAmount = BigDecimal.ZERO;
            for (Map<String, Object> stringObjectMap : mapList) {
                balancePayableAmount = balancePayableAmount.add(new BigDecimal(stringObjectMap.get("balancePayableAmount").toString()));
                balanceInvoiceAmount = balanceInvoiceAmount.add(new BigDecimal(stringObjectMap.get("balanceInvoiceAmount").toString()));
                receivingAmount = receivingAmount.add(new BigDecimal(stringObjectMap.get("receivingAmount").toString()));
                paymentAmount = paymentAmount.add(new BigDecimal(stringObjectMap.get("paymentAmount").toString()));
                invoiceAmount = invoiceAmount.add(new BigDecimal(stringObjectMap.get("invoiceAmount").toString()));
            }
            map.put("balancePayableAmount", balancePayableAmount);
            map.put("balanceInvoiceAmount", balanceInvoiceAmount);
            map.put("receivingAmount", receivingAmount);
            map.put("paymentAmount", paymentAmount);
            map.put("invoiceAmount", invoiceAmount);
            map.put("type", "汇总");
            mapList.add(map);
        }
        result.add(new ArrayList<>());
        objects = new ArrayList<>();
        objects.add("线下供应商对账单汇总:");
        result.add(objects);
        objects = new ArrayList<>();
        objects.add("单据类别");
        objects.add("收货金额");
        objects.add("付款金额");
        objects.add("应付账款");
        objects.add("开票金额");
        objects.add("发票结余/开票余额");
        result.add(objects);
        for (Map<String, Object> order : mapList) {
            objects = new ArrayList<>();
            objects.add(order.get("type"));
            objects.add(order.get("receivingAmount"));
            objects.add(order.get("paymentAmount"));
            objects.add(order.get("balancePayableAmount"));
            objects.add(order.get("invoiceAmount"));
            objects.add(order.get("balanceInvoiceAmount"));
            result.add(objects);
        }
        CommonUtils.exportByList(response, columnNameList, result, "供应商对账单");


    }

    /**
     * @param requestMap
     * @param isOnline   "线上“：”线下“
     * @return
     * @throws ParseException
     */
    private List<Map<String, Object>> getSupplier(Map<String, String> requestMap, String isOnline) throws ParseException {
        //当前页
        List<Map<String, Object>> page = financeService.getSupplier(requestMap, isOnline);
        //分页前数据
        List<Map<String, Object>> beforePage = null;
        //前期结余
        Map<String, Object> initial = financeService.getOrigAmount(requestMap, isOnline);
        //收货金额
        BigDecimal thisReceivingAmount = null;
        //付款金额
        BigDecimal thisPaymentAmount = null;
        //开票金额
        BigDecimal thisInvoiceAmount = null;
        //初期应付余额
        BigDecimal initPayableAmount = BigDecimal.ZERO;
        //初期开票余额
        BigDecimal initInvoiceAmount = BigDecimal.ZERO;
        if (initial != null && initial.size() > 0) {
            initPayableAmount = new BigDecimal(initial.get("payable") == null ? "0" : initial.get("payable").toString());
            initInvoiceAmount = new BigDecimal(initial.get("invoice") == null ? "0" : initial.get("invoice").toString());
        }

        //首页数据
        if ("0".equals(requestMap.get("offset"))) {
            Map<String, Object> index0 = new HashMap<>();
            index0.put("balancePayableAmount", initPayableAmount);
            index0.put("balanceInvoiceAmount", initInvoiceAmount);
            index0.put("Amount", " ");
            index0.put("type", "期初余额");
            page.add(0, index0);
            for (int i = 1; i < page.size(); i++) {
                thisReceivingAmount = BigDecimal.ZERO;
                thisPaymentAmount = BigDecimal.ZERO;
                thisInvoiceAmount = BigDecimal.ZERO;
                String plus = page.get(i).get("plus").toString();
                switch (plus) {
                    case "1":
                        thisReceivingAmount = new BigDecimal(page.get(i).get("Amount").toString());
                        page.get(i).put("receivingAmount", thisReceivingAmount);
                        break;
                    case "-1":
                        thisPaymentAmount = new BigDecimal(page.get(i).get("Amount").toString());
                        page.get(i).put("paymentAmount", thisPaymentAmount);
                        break;
                    case "-2":
                        thisInvoiceAmount = new BigDecimal(page.get(i).get("Amount").toString());
                        page.get(i).put("invoiceAmount", thisInvoiceAmount);
                        break;
                    default:
                        break;
                }

                BigDecimal lastPayableAmount = new BigDecimal(page.get(i - 1).get("balancePayableAmount").toString());
                BigDecimal lastInvoiceAmount = new BigDecimal(page.get(i - 1).get("balanceInvoiceAmount").toString());
                page.get(i).put("balancePayableAmount", lastPayableAmount.add(thisReceivingAmount).subtract(thisPaymentAmount).doubleValue());
                page.get(i).put("balanceInvoiceAmount", lastInvoiceAmount.add(thisReceivingAmount).subtract(thisInvoiceAmount).doubleValue());
            }
        } else {
            beforePage = financeService.getTopOrigAmount(requestMap, isOnline);
            BigDecimal payable = new BigDecimal(beforePage.get(0).get("payable").toString());
            BigDecimal invoice = new BigDecimal(beforePage.get(0).get("invoice").toString());
            for (int i = 0; i < page.size(); i++) {
                thisReceivingAmount = BigDecimal.ZERO;
                thisPaymentAmount = BigDecimal.ZERO;
                thisInvoiceAmount = BigDecimal.ZERO;
                String plus = page.get(i).get("plus").toString();
                BigDecimal amount = new BigDecimal(page.get(i).get("Amount").toString());
                switch (plus) {
                    case "1":
                        thisReceivingAmount = amount;
                        page.get(i).put("receivingAmount", thisReceivingAmount);
                        break;
                    case "-1":
                        thisPaymentAmount = amount;
                        page.get(i).put("paymentAmount", thisPaymentAmount);
                        break;
                    case "-2":
                        thisInvoiceAmount = amount;
                        page.get(i).put("invoiceAmount", thisInvoiceAmount);
                        break;
                    default:
                        break;
                }
                if (i == 0) {
                    page.get(i).put("balancePayableAmount", initPayableAmount.add(payable).add(thisReceivingAmount).subtract(thisPaymentAmount).doubleValue());
                    page.get(i).put("balanceInvoiceAmount", initInvoiceAmount.add(invoice).add(thisReceivingAmount).subtract(thisInvoiceAmount).doubleValue());
                } else {
                    BigDecimal lastPayableAmount = new BigDecimal(page.get(i - 1).get("balancePayableAmount").toString());
                    BigDecimal lastInvoiceAmount = new BigDecimal(page.get(i - 1).get("balanceInvoiceAmount").toString());
                    page.get(i).put("balancePayableAmount", lastPayableAmount.add(thisReceivingAmount).subtract(thisPaymentAmount).doubleValue());
                    page.get(i).put("balanceInvoiceAmount", lastInvoiceAmount.add(thisReceivingAmount).subtract(thisInvoiceAmount).doubleValue());
                }
            }
        }

        return page;
    }

    @PostMapping(value = "getCountSupplier")
    public Object getCountSupplier(HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        try {
            if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
                requestMap.put("startDate", request.getParameter("startDate"));
            }
            if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
                requestMap.put("endDate", request.getParameter("endDate"));
            }
            if (StringUtils.isNotBlank(request.getParameter("name"))) {
                requestMap.put("name", request.getParameter("name"));
            } else {
                return null;
            }
            List<Map<String, Object>> mapList = financeService.getSupplierCount(requestMap);
            if (mapList != null && mapList.size() > 0) {
                HashMap<String, Object> map = new HashMap<>();
                BigDecimal balancePayableAmount = BigDecimal.ZERO;
                BigDecimal balanceInvoiceAmount = BigDecimal.ZERO;
                BigDecimal receivingAmount = BigDecimal.ZERO;
                BigDecimal paymentAmount = BigDecimal.ZERO;
                BigDecimal invoiceAmount = BigDecimal.ZERO;
                for (Map<String, Object> stringObjectMap : mapList) {
                    balancePayableAmount = balancePayableAmount.add(new BigDecimal(stringObjectMap.get("balancePayableAmount").toString()));
                    balanceInvoiceAmount = balanceInvoiceAmount.add(new BigDecimal(stringObjectMap.get("balanceInvoiceAmount").toString()));
                    receivingAmount = receivingAmount.add(new BigDecimal(stringObjectMap.get("receivingAmount").toString()));
                    paymentAmount = paymentAmount.add(new BigDecimal(stringObjectMap.get("paymentAmount").toString()));
                    invoiceAmount = invoiceAmount.add(new BigDecimal(stringObjectMap.get("invoiceAmount").toString()));
                }
                map.put("balancePayableAmount", balancePayableAmount);
                map.put("balanceInvoiceAmount", balanceInvoiceAmount);
                map.put("receivingAmount", receivingAmount);
                map.put("paymentAmount", paymentAmount);
                map.put("invoiceAmount", invoiceAmount);
                map.put("type", "汇总");
                mapList.add(map);
            }
            result.put("rows", mapList);
            result.put("total", 3);
            return result;
        } catch (ParseException e) {
            return new Result("400", "异常", null);
        }
    }

    @PostMapping(value = "/download/excel")
    public void download(HttpServletResponse response, HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
            requestMap.put("startDate", request.getParameter("startDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
            requestMap.put("endDate", request.getParameter("endDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("name"))) {
            requestMap.put("name", request.getParameter("name"));
        } else {
            return;
        }
        requestMap.put("limit", "9999999");
        requestMap.put("offset", "0");
        List<Map<String, Object>> page = financeService.getSupplier(requestMap, "线上");

        List<String> columnNameList = new ArrayList<>();
        columnNameList.add("日期");
        columnNameList.add("合同编号");
        columnNameList.add("单据类别");
        columnNameList.add("收货金额");
        columnNameList.add("付款金额");
        columnNameList.add("应付账款");
        columnNameList.add("开票金额");
        columnNameList.add("发票结余");

        List<Object> objects;
        for (Map<String, Object> objectMap : page) {
            objects = new ArrayList<>();
            objects.add(objectMap.get("voucherdate"));
            objects.add(objectMap.get("code"));
            objects.add(objectMap.get("type"));
            objects.add("1".equals(objectMap.get("plus")) ? objectMap.get("Amount") : "");
            objects.add("-1".equals(objectMap.get("plus")) ? objectMap.get("Amount") : "");
            objects.add("0".equals(objectMap.get("plus")) ? objectMap.get("Amount") : "");
        }
    }

    /**
     * 文件上传
     * <p>
     * 1. 创建excel对应的实体对象
     * <p>
     * 2. 由于默认异步读取excel，所以需要创建excel一行一行的回调监听器
     * <p>
     * 3. 直接读即可
     */
    @PostMapping("upload")
    @ResponseBody
    public String upload(MultipartFile file) throws IOException {
        // 新版
        // EasyExcel.read(file.getInputStream(), UploadData.class, new UploadDataListener(deptStaffRepository)).sheet().doRead();
        // 旧版
        InputStream inputStream = file.getInputStream();
        ExcelListener listener = new ExcelListener();
        ExcelReader excelReader = new ExcelReader(inputStream, ExcelTypeEnum.XLSX, null, listener);
        excelReader.read(new Sheet(1, 1, UploadData.class));
        List<Object> list = listener.getDatas();
        List<DeptStaff> deptStaffList = new ArrayList<>();
        deptStaffRepository.deleteAll();
        deptStaffRepository.flush();
        for (int i = 0; i < list.size(); i++) {
            DeptStaff deptStaff = new DeptStaff();
            BeanUtils.copyProperties((UploadData) list.get(i), deptStaff);
            deptStaffList.add(deptStaff);
        }
        deptStaffRepository.saveAll(deptStaffList);
        return "success";
    }

    @PostMapping("uploadSalesman")
    @ResponseBody
    public String uploadSalesman(MultipartFile file) throws IOException {
        // 旧版
        InputStream is = null;
        try {
            is = file.getInputStream();

            Workbook book = new XSSFWorkbook(is);
            org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(0);
            int rows = sheet.getLastRowNum();
            List memberSalesmans = new ArrayList<MemberSalesman>();
            MemberSalesman memberSalesman = null;
            for (int i = 1; i <= rows; i++) {
                Row r = sheet.getRow(i);
//                System.out.println(r.getCell(0)+"==="+r.getCell(1)+"==="+r.getCell(2));
                memberSalesman = new MemberSalesman();
                String name  = null;
                switch (r.getCell(0).getCellTypeEnum()) {
                    case NUMERIC:
                        int numericCellValue = (int) r.getCell(0).getNumericCellValue();
                        name = numericCellValue+"";
                        break;
                    case STRING:
                        name = r.getCell(0).getStringCellValue();
                        break;
                    default:
                        break;
                }
                memberSalesman.setName(name);
                memberSalesman.setTopSaleman(r.getCell(1) == null ? null : r.getCell(1).getStringCellValue().trim());
                memberSalesman.setSecondSaleman(r.getCell(2) == null ? null : r.getCell(2).getStringCellValue());
                memberSalesmans.add(memberSalesman);
            }
            memberSalemanRepository.deleteAll();
            memberSalemanRepository.flush();
            memberSalemanRepository.saveAll(memberSalesmans);

        } catch (IOException e) {
            return "err";
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {

            }
        }

        return "success";
    }



}
