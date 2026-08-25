package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.entity.TkTicketFile;
import com.myworkflow.module.ticket.mapper.TkTicketFileMapper;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketFileService {

    private final TkTicketFileMapper fileMapper;
    private final TkTicketMapper ticketMapper;
    private final PermissionService permissionService;

    @Value("${myworkflow.upload-dir:./data/ticket-files}")
    private String uploadDir;

    public TkTicketFile upload(MultipartFile file, Long ticketId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择文件");
        }
        UserContext ctx = UserContext.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BizException("请先登录");
        }
        if (ticketId != null) {
            TkTicket ticket = ticketMapper.selectById(ticketId);
            if (ticket == null) {
                throw new BizException("工单不存在");
            }
            assertCanWrite(ticket);
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            original = "file";
        }
        original = original.replace("\\", "_").replace("/", "_");
        String stored = UUID.randomUUID().toString().replace("-", "") + "_" + original;
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(stored);
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
        TkTicketFile rec = new TkTicketFile();
        rec.setTicketId(ticketId);
        rec.setFileName(original);
        rec.setContentType(file.getContentType());
        rec.setFileSize(file.getSize());
        rec.setStoragePath(stored);
        rec.setTenantId(ctx.getTenantId());
        fileMapper.insert(rec);
        return rec;
    }

    public TkTicketFile info(Long id) {
        TkTicketFile rec = require(id);
        assertCanRead(rec);
        return rec;
    }

    public List<TkTicketFile> infos(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<TkTicketFile> list = fileMapper.selectList(new LambdaQueryWrapper<TkTicketFile>().in(TkTicketFile::getId, ids));
        List<TkTicketFile> out = new ArrayList<>();
        for (TkTicketFile rec : list) {
            try {
                assertCanRead(rec);
                out.add(rec);
            } catch (BizException ignored) {
                // skip files outside scope
            }
        }
        return out;
    }

    public ResponseEntity<Resource> download(Long id) {
        TkTicketFile rec = require(id);
        assertCanRead(rec);
        Path path = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(rec.getStoragePath());
        if (!Files.exists(path)) {
            throw new BizException("文件不存在或已被删除");
        }
        String name = rec.getFileName();
        try {
            name = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ignored) {
            // UTF-8 always present
        }
        String ct = StringUtils.hasText(rec.getContentType()) ? rec.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .contentType(MediaType.parseMediaType(ct))
                .body(new FileSystemResource(path.toFile()));
    }

    public void bindFromFormData(Long ticketId, Map<String, Object> formData) {
        if (ticketId == null) {
            return;
        }
        List<Long> ids = extractFileIds(formData);
        if (ids.isEmpty()) {
            return;
        }
        List<TkTicketFile> files = fileMapper.selectList(new LambdaQueryWrapper<TkTicketFile>().in(TkTicketFile::getId, ids));
        Long uid = UserContext.currentUserId();
        for (TkTicketFile rec : files) {
            if (rec.getTicketId() != null && !rec.getTicketId().equals(ticketId)) {
                continue;
            }
            if (rec.getTicketId() == null && uid != null && rec.getCreateBy() != null && !uid.equals(rec.getCreateBy())) {
                continue;
            }
            rec.setTicketId(ticketId);
            fileMapper.updateById(rec);
        }
    }

    public static List<Long> extractFileIds(Map<String, Object> formData) {
        List<Long> ids = new ArrayList<>();
        if (formData == null) {
            return ids;
        }
        for (Object v : formData.values()) {
            collectIds(v, ids);
        }
        return ids;
    }

    private static void collectIds(Object v, List<Long> ids) {
        if (v instanceof Number) {
            ids.add(((Number) v).longValue());
            return;
        }
        if (v instanceof String && ((String) v).matches("\\d{5,}")) {
            try {
                ids.add(Long.valueOf((String) v));
            } catch (NumberFormatException ignored) {
                // skip
            }
            return;
        }
        if (v instanceof Collection) {
            for (Object item : (Collection<?>) v) {
                if (item instanceof Map) {
                    Object id = ((Map<?, ?>) item).get("id");
                    collectIds(id, ids);
                } else {
                    collectIds(item, ids);
                }
            }
        }
    }

    private TkTicketFile require(Long id) {
        TkTicketFile rec = fileMapper.selectById(id);
        if (rec == null) {
            throw new BizException("文件不存在");
        }
        return rec;
    }

    private void assertCanRead(TkTicketFile rec) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        if (ctx.isAdmin() || permissionService.allScope()) {
            return;
        }
        if (rec.getTicketId() == null) {
            if (rec.getCreateBy() != null && rec.getCreateBy().equals(ctx.getUserId())) {
                return;
            }
            throw new BizException(403, "无权访问该文件");
        }
        TkTicket ticket = ticketMapper.selectById(rec.getTicketId());
        if (ticket == null) {
            throw new BizException("工单不存在");
        }
        assertTicketVisible(ticket);
    }

    private void assertCanWrite(TkTicket ticket) {
        assertTicketVisible(ticket);
        if (!"DRAFT".equals(ticket.getStatus()) && !"REJECTED".equals(ticket.getStatus())) {
            throw new BizException("当前状态不可上传附件");
        }
    }

    private void assertTicketVisible(TkTicket ticket) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        if (ctx.isAdmin() || permissionService.allScope()) {
            return;
        }
        Long starter = ticket.getStarterId();
        if (permissionService.deptScope()) {
            List<Long> ids = permissionService.scopeUserIds();
            if (ids != null && starter != null && ids.contains(starter)) {
                return;
            }
            throw new BizException(403, "无权访问该文件");
        }
        if (starter == null || !starter.equals(ctx.getUserId())) {
            throw new BizException(403, "无权访问该文件");
        }
    }
}
