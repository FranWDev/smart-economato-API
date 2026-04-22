/* eslint-disable @typescript-eslint/unbound-method */
import { InventoryClient } from '../../mcp/inventory.client';
import { GetProductDeepTool } from '../read/get-product-deep.tool';
import { GetSystemContextTool } from '../utility/get-system-context.tool';

describe('Tools', () => {
  it('GetProductDeepTool validates and calls expected endpoint', async () => {
    const tool = new GetProductDeepTool();
    const client = {
      get: jest.fn().mockResolvedValue({ id: 7, name: 'Flour' }),
    } as unknown as InventoryClient;

    const result = await tool.execute({ id: 7 }, client, 'Bearer abc');
    const getMock = client.get as jest.Mock;

    expect(tool.name).toBe('get_product_deep');
    expect(result).toEqual({ id: 7, name: 'Flour' });
    expect(getMock).toHaveBeenCalledWith(
      '/api/mcp/products/7/deep',
      'Bearer abc',
    );
  });

  it('GetProductDeepTool throws on invalid args', async () => {
    const tool = new GetProductDeepTool();
    const client = {
      get: jest.fn(),
    } as unknown as InventoryClient;

    await expect(
      tool.execute({ id: '7' }, client, 'Bearer abc'),
    ).rejects.toThrow();
    const getMock = client.get as jest.Mock;
    expect(getMock).not.toHaveBeenCalled();
  });

  it('GetSystemContextTool calls context endpoint with JWT', async () => {
    const tool = new GetSystemContextTool();
    const client = {
      get: jest.fn().mockResolvedValue({ status: 'ok' }),
    } as unknown as InventoryClient;

    const result = await tool.execute({}, client, 'Bearer xyz');
    const getMock = client.get as jest.Mock;

    expect(tool.name).toBe('get_system_context');
    expect(result).toEqual({ status: 'ok' });
    expect(getMock).toHaveBeenCalledWith('/api/mcp/context', 'Bearer xyz');
  });
});
