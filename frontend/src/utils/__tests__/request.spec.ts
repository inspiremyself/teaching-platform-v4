import { beforeEach, describe, expect, it, vi } from 'vitest';

const h = vi.hoisted(() => ({
  reqFns: [] as any[],
  resOk: [] as any[],
  resErr: [] as any[],
  messageError: vi.fn(),
  storage: {
    getItem: vi.fn(() => 'tk'),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  },
  session: {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  },
  location: { href: '' },
}));

vi.mock('axios', () => {
  const instance = {
    interceptors: {
      request: { use: (fn: any) => h.reqFns.push(fn) },
      response: {
        use: (ok: any, err: any) => {
          h.resOk.push(ok);
          h.resErr.push(err);
        },
      },
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  };
  return { default: { create: () => instance } };
});

vi.mock('element-plus', () => ({ ElMessage: { error: h.messageError } }));

const envelope = (code: number, data: unknown = {}) => ({
  data: { code, message: 'boom', data, timestamp: '' },
  config: {},
});

async function load() {
  vi.resetModules();
  h.reqFns.length = 0;
  h.resOk.length = 0;
  h.resErr.length = 0;
  vi.stubGlobal('localStorage', h.storage);
  vi.stubGlobal('sessionStorage', h.session);
  vi.stubGlobal('window', { location: h.location });
  return import('@/utils/request');
}

describe('request.ts 解包与登录态', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.location.href = '';
  });

  it('成功响应解包为 data', async () => {
    await load();
    expect(await h.resOk[0](envelope(0, { id: 7 }))).toEqual({ id: 7 });
  });

  it('两组错误码常量保持独立', async () => {
    const mod = await load();
    expect([...mod.SILENT_CODES]).toEqual([40901, 40101, 40103, 40903, 40102]);
    expect([...mod.AUTH_FAILURE_CODES]).toEqual([401, 40100, 40102]);
  });

  it('业务错误码抛出 ApiError 并弹 toast', async () => {
    await load();
    await expect(h.resOk[0](envelope(40001))).rejects.toMatchObject({
      name: 'ApiError',
      code: 40001,
    });
    expect(h.messageError).toHaveBeenCalledWith('boom');
    expect(h.location.href).toBe('');
  });

  it('40901 静默且不登出', async () => {
    await load();
    await expect(h.resOk[0](envelope(40901))).rejects.toMatchObject({ code: 40901 });
    expect(h.messageError).not.toHaveBeenCalled();
    expect(h.storage.removeItem).not.toHaveBeenCalled();
  });

  it('40903 静默且不登出', async () => {
    await load();
    await expect(h.resOk[0](envelope(40903))).rejects.toMatchObject({ code: 40903 });
    expect(h.messageError).not.toHaveBeenCalled();
    expect(h.location.href).toBe('');
  });

  it('401 清 token 并跳登录', async () => {
    await load();
    await expect(h.resOk[0](envelope(401))).rejects.toMatchObject({ code: 401 });
    expect(h.storage.removeItem).toHaveBeenCalledWith('token');
    expect(h.location.href).toBe('/login');
  });

  it('40100 清 token 并跳登录', async () => {
    await load();
    await expect(h.resOk[0](envelope(40100))).rejects.toMatchObject({ code: 40100 });
    expect(h.storage.removeItem).toHaveBeenCalledWith('token');
    expect(h.location.href).toBe('/login');
  });

  it('40102 走踢出提示且不残留 token', async () => {
    await load();
    await expect(h.resOk[0](envelope(40102))).rejects.toMatchObject({ code: 40102 });
    expect(h.session.setItem).toHaveBeenCalledWith('kicked_out_notice', expect.any(String));
    expect(h.storage.removeItem).toHaveBeenCalledWith('token');
    expect(h.location.href).toBe('/login');
  });

  it('无响应体的网络错误原样抛出', async () => {
    await load();
    const networkError = { message: 'network down', config: {} };
    await expect(h.resErr[0](networkError)).rejects.toMatchObject({ message: 'network down' });
  });

  it('错误响应带业务 code 时转为 ApiError', async () => {
    await load();
    const httpError = {
      response: { data: { code: 40001, message: 'bad', data: null, timestamp: '' } },
      config: {},
    };
    await expect(h.resErr[0](httpError)).rejects.toMatchObject({ code: 40001 });
  });

  it.each([40904, 40905, 40028])('%i 抛 ApiError 并弹 toast', async code => {
    await load();
    await expect(h.resOk[0](envelope(code))).rejects.toMatchObject({
      name: 'ApiError',
      code,
    });
    expect(h.messageError).toHaveBeenCalledWith('boom');
    expect(h.location.href).toBe('');
  });

  it('请求拦截器注入 Authorization', async () => {
    await load();
    const config: any = { headers: {} };
    const out = h.reqFns[0](config);
    expect(out.headers.Authorization).toBe('Bearer tk');
  });
});
