package com.myworkflow.module.ticket.controller;

import com.myworkflow.common.result.R;
import com.myworkflow.module.ticket.entity.TkTicketFile;
import com.myworkflow.module.ticket.service.TicketFileService;
import com.myworkflow.module.ticket.service.TicketDataAccessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Api(tags = "工单附件")
@RestController
@RequestMapping("/ticket/files")
@RequiredArgsConstructor
public class TicketFileController {

    private final TicketFileService ticketFileService;
    private final TicketDataAccessService ticketDataAccessService;

    @ApiOperation("上传附件，表单 file 字段存返回的 id 列表")
    @PostMapping
    public R<TkTicketFile> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) Long ticketId) {
        ticketDataAccessService.assertTicketDataAccess(ticketId);
        return R.ok(ticketFileService.upload(file, ticketId));
    }

    @ApiOperation("附件元数据")
    @GetMapping("/{id}/info")
    public R<TkTicketFile> info(@PathVariable Long id) {
        TkTicketFile file = ticketFileService.info(id);
        ticketDataAccessService.assertTicketDataAccess(file.getTicketId());
        return R.ok(file);
    }

    @ApiOperation("批量元数据")
    @GetMapping
    public R<List<TkTicketFile>> infos(@RequestParam String ids) {
        List<Long> list = new ArrayList<>();
        if (ids != null) {
            for (String p : ids.split(",")) {
                if (p.trim().isEmpty()) {
                    continue;
                }
                list.add(Long.valueOf(p.trim()));
            }
        }
        List<TkTicketFile> files = ticketFileService.infos(list);
        for (TkTicketFile file : files) {
            ticketDataAccessService.assertTicketDataAccess(file.getTicketId());
        }
        return R.ok(files);
    }

    @ApiOperation("下载附件")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        TkTicketFile file = ticketFileService.info(id);
        ticketDataAccessService.assertTicketDataAccess(file.getTicketId());
        return ticketFileService.download(id);
    }
}
